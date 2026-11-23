package com.aiinterview.formal.persistence;

import com.aiinterview.formal.domain.InterviewRecords;
import com.aiinterview.formal.domain.InterviewSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

class SqlInterviewStoreLocalAudioTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    Path files;

    @Test
    void sharedDatabaseContainsOnlyAReferenceAndOnlyTheOwnerCanReadTheLocalAudio() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("formal-audio-" + UUID.randomUUID() + ";MODE=MySQL")
                .addScripts("classpath:schema-h2.sql", "classpath:migrations/006_formal.sql").build();
        try {
            verifyLocalAudio(new JdbcTemplate(database), new ObjectMapper());
        } finally {
            database.shutdown();
        }
    }

    private void verifyLocalAudio(JdbcTemplate jdbc, ObjectMapper json) {
        var session = new InterviewSession();
        session.id = UUID.randomUUID();
        session.studentId = UUID.randomUUID().toString();
        session.roleId = 1L;
        session.state = InterviewSession.State.AWAITING_AUDIO;
        jdbc.update("""
                INSERT INTO sys_user (id, account, password_hash, role, status, nickname, updated_by, created_at, updated_at)
                VALUES (?, 'audiotest', 'placeholder', 'STUDENT', 'ACTIVE', 'Test', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, session.studentId, session.studentId);

        var store = new SqlInterviewStore(jdbc.getDataSource(), json, (owner, id) -> {}, files);
        store.insert(session);
        var turn = UUID.randomUUID();
        byte[] recording = "RIFFtestWAVE".getBytes(StandardCharsets.US_ASCII);
        store.change(session.studentId, session.id, tx -> {
            tx.saveAudio(turn, new InterviewRecords.Audio("audio/wav", recording));
            return null;
        });

        byte[] stored = jdbc.queryForObject("SELECT recording FROM fi_audio WHERE session_id = ? AND turn_id = ?",
                byte[].class, session.id.toString(), turn.toString());
        assertThat(new String(stored, StandardCharsets.US_ASCII)).startsWith("local:");
        assertThat(store.audio(session.studentId, session.id, turn).orElseThrow().bytes()).isEqualTo(recording);
        assertThat(store.audio(UUID.randomUUID().toString(), session.id, turn)).isEmpty();
        var otherDevice = new SqlInterviewStore(jdbc.getDataSource(), json, (owner, id) -> {}, files.resolve("other-device"));
        assertThat(otherDevice.audio(session.studentId, session.id, turn)).isEmpty();
    }
}
