package com.aiinterview.formal.persistence;

import com.aiinterview.formal.domain.InterviewProblem;
import com.aiinterview.formal.domain.InterviewRecords;
import com.aiinterview.formal.domain.InterviewSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.sql.DataSource;

public final class SqlInterviewStore {
    private static final String LOCAL_AUDIO_PREFIX = "local:";
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final BiConsumer<String, UUID> completion;
    private final Path localAudioRoot;

    public SqlInterviewStore(DataSource dataSource, ObjectMapper json, BiConsumer<String, UUID> completion, Path localAudioRoot) {
        this.dataSource = dataSource;
        this.json = json;
        this.completion = completion;
        this.localAudioRoot = localAudioRoot;
    }

    public void insert(InterviewSession session) {
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

    public <T> T change(String studentId, UUID sessionId, Function<Transaction, T> change) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                InterviewSession session = read(connection, studentId, sessionId, true)
                        .orElseThrow(() -> InterviewProblem.notFound("面试会话不存在"));
                boolean wasEnded = session.state == InterviewSession.State.ENDED;
                T result = change.apply(new Transaction(connection, session, localAudioRoot));
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

    public Optional<InterviewSession> find(String studentId, UUID sessionId) {
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

    public Optional<InterviewRecords.Audio> audio(String studentId, UUID sessionId, UUID turnId) {
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
                    var mediaType = rows.getString("media_type");
                    var recording = rows.getBytes("recording");
                    if (recording.length == LOCAL_AUDIO_PREFIX.length() + 36) {
                        String reference = new String(recording, StandardCharsets.US_ASCII);
                        if (reference.startsWith(LOCAL_AUDIO_PREFIX)) return readLocalAudio(mediaType, reference);
                    }
                    return Optional.of(new InterviewRecords.Audio(mediaType, recording));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("无法读取面试音频", e);
        }
    }

    private Optional<InterviewRecords.Audio> readLocalAudio(String mediaType, String reference) {
        if (localAudioRoot == null) return Optional.empty();
        try {
            var id = UUID.fromString(reference.substring(LOCAL_AUDIO_PREFIX.length()));
            try (var stream = Files.newInputStream(localAudioRoot.resolve(id + ".bin"))) {
                var bytes = stream.readNBytes(InterviewRecords.Audio.MAX_BYTES + 1);
                if (bytes.length == 0 || bytes.length > InterviewRecords.Audio.MAX_BYTES) {
                    throw new IllegalStateException("本机面试音频大小无效");
                }
                return Optional.of(new InterviewRecords.Audio(mediaType, bytes));
            }
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("无法读取本机面试音频", e);
        }
    }

    public record Transaction(Connection connection, InterviewSession session, Path localAudioRoot) {
        public void saveAudio(UUID turnId, InterviewRecords.Audio audio) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO fi_audio (session_id, turn_id, media_type, recording) VALUES (?, ?, ?, ?)
                    """)) {
                statement.setString(1, session.id.toString());
                statement.setString(2, turnId.toString());
                statement.setString(3, audio.mediaType());
                UUID fileId = localAudioRoot == null ? null : UUID.randomUUID();
                byte[] stored = fileId == null ? audio.bytes()
                        : (LOCAL_AUDIO_PREFIX + fileId).getBytes(StandardCharsets.US_ASCII);
                statement.setBytes(4, stored);
                statement.executeUpdate();
                if (fileId != null) {
                    Files.createDirectories(localAudioRoot);
                    Files.write(localAudioRoot.resolve(fileId + ".bin"), audio.bytes(), StandardOpenOption.CREATE_NEW);
                }
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("无法保存面试音频", e);
            }
        }
    }
}
