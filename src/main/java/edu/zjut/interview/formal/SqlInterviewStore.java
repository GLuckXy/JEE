package edu.zjut.interview.formal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.BiConsumer;
import javax.sql.DataSource;

final class SqlInterviewStore {
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final BiConsumer<String, UUID> completion;

    SqlInterviewStore(DataSource dataSource, ObjectMapper json, BiConsumer<String, UUID> completion) {
        this.dataSource = dataSource;
        this.json = json;
        this.completion = completion;
    }

    void insert(InterviewSession session) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO fi_session (session_id, student_id, role_id, created_at, ended_at, payload) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, session.id.toString());
            statement.setString(2, session.studentId);
            statement.setLong(3, session.roleId);
            statement.setLong(4, session.createdAt);
            statement.setLong(5, session.endedAt);
            statement.setString(6, json.writeValueAsString(session));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException("无法保存面试会话", e);
        }
    }

    <T> T change(String studentId, UUID sessionId, Function<Transaction, T> change) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                InterviewSession session = read(connection, studentId, sessionId, true)
                        .orElseThrow(() -> InterviewProblem.notFound("面试会话不存在"));
                boolean wasEnded = session.state == InterviewSession.State.ENDED;
                T result = change.apply(new Transaction(connection, session));
                try (var statement = connection.prepareStatement(
                        "UPDATE fi_session SET payload = ?, ended_at = ?, end_reason = ? WHERE session_id = ? AND student_id = ?")) {
                    statement.setString(1, json.writeValueAsString(session));
                    statement.setLong(2, session.endedAt);
                    statement.setString(3, session.endReason == null ? null : session.endReason.name());
                    statement.setString(4, sessionId.toString());
                    statement.setString(5, studentId);
                    statement.executeUpdate();
                }
                connection.commit();
                if (!wasEnded && session.state == InterviewSession.State.ENDED) completion.accept(studentId, sessionId);
                return result;
            } catch (RuntimeException | SQLException | JsonProcessingException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException("无法更新面试会话", e);
        }
    }

    Optional<InterviewSession> find(String studentId, UUID sessionId) {
        try (var connection = dataSource.getConnection()) {
            return read(connection, studentId, sessionId, false);
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException("无法读取面试会话", e);
        }
    }

    private Optional<InterviewSession> read(Connection connection, String studentId, UUID sessionId, boolean lock)
            throws SQLException, JsonProcessingException {
        String query = "SELECT student_id, payload FROM fi_session WHERE session_id = ? AND student_id = ?";
        try (var statement = connection.prepareStatement(lock ? query + " FOR UPDATE" : query)) {
            statement.setString(1, sessionId.toString());
            statement.setString(2, studentId);
            try (var rows = statement.executeQuery()) {
                if (rows.next() && studentId.equals(rows.getString("student_id"))) {
                    return Optional.of(json.readValue(rows.getString("payload"), InterviewSession.class));
                }
                return Optional.empty();
            }
        }
    }

    Optional<InterviewRecords.Audio> audio(String studentId, UUID sessionId, UUID turnId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT s.student_id, a.media_type, a.recording
                     FROM fi_audio a JOIN fi_session s ON s.session_id = a.session_id
                     WHERE a.session_id = ? AND a.turn_id = ? AND s.student_id = ?
                     """)) {
            statement.setString(1, sessionId.toString());
            statement.setString(2, turnId.toString());
            statement.setString(3, studentId);
            try (var rows = statement.executeQuery()) {
                if (rows.next() && studentId.equals(rows.getString("student_id"))) {
                    return Optional.of(new InterviewRecords.Audio(rows.getString("media_type"), rows.getBytes("recording")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("无法读取面试音频", e);
        }
    }

    record Transaction(Connection connection, InterviewSession session) {
        void saveAudio(UUID turnId, InterviewRecords.Audio audio) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO fi_audio (session_id, turn_id, media_type, recording) VALUES (?, ?, ?, ?)
                    """)) {
                statement.setString(1, session.id.toString());
                statement.setString(2, turnId.toString());
                statement.setString(3, audio.mediaType());
                statement.setBytes(4, audio.bytes());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("无法保存面试音频", e);
            }
        }
    }
}
