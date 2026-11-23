package com.aiinterview;

import com.aiinterview.formal.service.ModelClient;
import com.aiinterview.report.service.engine.ReportAggregator;
import com.aiinterview.user.persistence.User;
import com.aiinterview.user.persistence.UserMapper;
import com.aiinterview.voice.service.SpeechProvider;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class PlatformIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired MutableClock clock;
    @MockitoBean ModelClient model;
    @MockitoBean SpeechProvider speech;
    @MockitoSpyBean ReportAggregator aggregator;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        String url = System.getenv("VERIFY_PLATFORM_MYSQL_URL");
        if (url == null || url.isBlank()) {
            properties.add("spring.datasource.url", () -> "jdbc:h2:mem:platform_integration_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            return;
        }
        if (!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?/[^/\\s?#]+(?:\\?.*)?"))
            throw new IllegalArgumentException("MySQL verification must use a database on localhost or 127.0.0.1");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("VERIFY_PLATFORM_MYSQL_USER"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("VERIFY_PLATFORM_MYSQL_PASSWORD", ""));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.sql.init.mode", () -> "never");
    }

    @Test void teachingContentIsOptInDoesNotOverwriteAndKeepsDemosInDraft() throws Exception {
        var student = actor(false);
        var admin = actor(true);
        String endpoint = "/api/admin/content/teaching-content";
        send(post(endpoint), null, null).andExpect(status().isUnauthorized());
        send(post(endpoint), student, null).andExpect(status().isForbidden());
        var jobsPath = "/api/admin/content/jobs";
        var javaJobs = get(jobsPath).param("keyword", "Java 后端");
        assertThat(good(javaJobs, admin, null).path("total").asInt()).isZero();
        var realJob = good(post(jobsPath), admin, Map.of("name", "Java 后端", "description", "原有内容不应被覆盖",
                "requirements", "Java", "interviewFocus", "排查问题", "enabled", true, "sortOrder", 5));
        var imported = good(post(endpoint), admin, null);
        assertThat(imported.path("jobs").asInt()).isEqualTo(2);
        assertThat(imported.path("categories").asInt()).isEqualTo(4);
        assertThat(imported.path("questions").asInt()).isEqualTo(8);
        assertThat(imported.path("drafts").asInt()).isEqualTo(2);
        var repeat = good(post(endpoint), admin, null);
        assertThat(repeat.path("jobs").asInt()).isZero();
        assertThat(repeat.path("skippedJobs").asInt()).isEqualTo(2);
        assertThat(good(get(jobsPath + "/{id}", realJob.path("id").asLong()), admin, null).path("description").asText())
                .isEqualTo("原有内容不应被覆盖");
        var publicJobs = good(get("/api/content/jobs").param("keyword", "【测试】Java 后端"), student, null);
        assertThat(publicJobs.path("total").asInt()).isEqualTo(1);
        String jobId = publicJobs.at("/records/0/id").asText();
        var questions = good(get("/api/training/questions").param("roleId", jobId), student, null);
        assertThat(questions).hasSize(4);
        for (var question : questions) assertThat(question.has("referencePoints")).isFalse();
        var demos = good(get("/api/content/demos").param("jobId", jobId), student, null);
        assertThat(demos.path("total").asInt()).isZero();
        var drafts = good(get("/api/admin/content/demos").param("jobId", jobId), admin, null);
        assertThat(drafts.at("/records/0/status").asText()).isEqualTo("DRAFT");
    }

    @Test void sharedContentTrainingHistoryAndRetrainPreserveOwnershipAndSnapshots() throws Exception {
        for (String route : List.of("/api/content/jobs", "/api/training/roles", "/api/formal-interviews/options", "/api/reports/history"))
            mvc.perform(get(route)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        var student = actor(false);
        var other = actor(false);
        var admin = actor(true);
        var content = fixtures(admin);
        mvc.perform(get("/api/training/roles").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/content/questions").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/content/questions/{id}", content.primary()).header("Authorization", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.referencePoints").doesNotExist());
        var demo = good(post("/api/admin/content/demos"), admin, Map.of("jobId", content.job(), "title", "测试示范", "introduction", "演示流程",
                "sourceNote", "自动化数据", "stages", List.of(Map.of("sortOrder", 1, "title", "开场", "interviewerQuestion", "请做自我介绍",
                        "sampleAnswer", "我参与课程项目。", "explanation", "交代职责"))));
        long demoId = demo.path("id").asLong();
        send(get("/api/content/demos/{id}", demoId), student, null).andExpect(status().isNotFound());
        good(patch("/api/admin/content/demos/{id}/status", demoId), admin, Map.of("status", "PUBLISHED"));
        assertThat(good(get("/api/content/demos/{id}", demoId), student, null).path("stages").size()).isEqualTo(1);

        var session = createTraining(student, content, "NORMAL", List.of(content.primary(), content.similar()));
        String id = session.path("id").asText();
        assertThat(session.path("question").has("referencePoints")).isFalse();
        assertThat(UUID.fromString(id)).isNotNull();
        var training = "/api/training/sessions/" + id;
        var audio = wav((byte) 42);
        var asset = parse(send(multipart("/api/voice/audio").file(audio("file", audio)), student, null).andExpect(status().isOk()));
        String assetId = asset.path("id").asText();
        send(get("/api/voice/audio/{id}", assetId), other, null).andExpect(status().isNotFound());
        send(get("/api/voice/audio/{id}", assetId), admin, null).andExpect(status().isForbidden());
        send(get("/api/voice/audio/{id}", assetId), student, null).andExpect(content().bytes(audio));
        send(get(training), other, null).andExpect(status().isNotFound());
        good(post(training + "/pause"), student, Map.of("position", 0, "roundNumber", 1, "text", "保留草稿", "audioId", assetId));
        assertThat(good(get(training), student, null).path("draft").asText()).isEqualTo("保留草稿");
        good(post(training + "/resume"), student, cursor(0, 1));
        var answer = submitAnswer(training, student, 1, "事务隔离避免脏读", assetId);
        good(post("/api/training/answers/{id}/evaluation", answer.path("id").asText()), student, Map.of());
        good(post(training + "/redraft"), student, cursor(0, 1));
        submitAnswer(training, student, 2, "事务隔离可以防止脏读，并要区分不同隔离级别。", assetId);

        good(put("/api/admin/content/questions/{id}", content.primary()), admin, question(content, "新题干", "新参考要点", false));
        assertThat(good(get("/api/training/questions").param("roleId", "" + content.job()), student, null).size()).isEqualTo(1);
        send(post("/api/training/sessions"), student, Map.of("mode", "NORMAL", "roleId", content.job(), "questionIds", List.of(content.primary())))
                .andExpect(status().isBadRequest());
        good(post(training + "/end"), student, cursor(0, 2));

        String reportPath = "/api/reports/sessions/" + id;
        JsonNode report = waitForReport(student, reportPath);
        assertThat(report.path("summary").path("answeredCount").asInt()).isEqualTo(1);
        assertThat(report.path("summary").path("dimensionScores").size()).isEqualTo(5);
        assertThat(report.path("answers")).hasSize(3);
        assertThat(report.at("/answers/0/question").asText()).isEqualTo("原题干");
        assertThat(report.at("/answers/0/referencePoints/0").asText()).isEqualTo("事务隔离避免脏读");
        assertThat(report.at("/answers/2/referencePoints")).isEmpty();
        assertThat(report.at("/answers/2/evaluation").isNull()).isTrue();
        assertThat(good(post(reportPath + "/generate"), student, null)).isEqualTo(report);
        send(get(reportPath), other, null).andExpect(status().isNotFound());
        send(get(reportPath), admin, null).andExpect(status().isForbidden());
        assertThat(good(get("/api/reports/history"), other, null).path("total").asInt()).isZero();
        assertThat(good(get("/api/reports/history").param("mode", "NORMAL"), student, null).at("/records/0/sessionId").asText()).isEqualTo(id);

        String answerId = report.at("/answers/1/id").asText();
        send(get("/api/reports/evaluations/{id}", answerId), other, null).andExpect(status().isNotFound());
        var link = good(post(reportPath + "/retrain"), student, Map.of("answerId", answerId));
        var targetPath = "/api/training/sessions/" + link.path("targetSessionId").asText();
        var target = good(get(targetPath), student, null);
        assertThat(target.path("mode").asText()).isEqualTo("SPECIAL");
        assertThat(target.at("/question/id").asLong()).isEqualTo(content.similar());
        good(post(targetPath + "/pause"), student, Map.of("position", 0, "roundNumber", 1, "text", "专项草稿"));
        assertThat(good(post(targetPath + "/resume"), student, cursor(0, 1)).path("draft").asText()).isEqualTo("专项草稿");
        assertThat(good(get(reportPath), student, null).at("/retrainLinks/0/targetSessionId")).isEqualTo(link.path("targetSessionId"));
    }

    @Test void formalUsesConfirmedProjectTimedAudioAndACompletedReport() throws Exception {
        var student = actor(false);
        var outsider = actor(false);
        var admin = actor(true);
        var content = fixtures(admin);
        String project = confirmedProject(student);
        var request = Map.of("roleId", content.job(), "projectId", project);
        String root = "/api/formal-interviews";
        send(post(root), student, request).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ASR_UNAVAILABLE"));
        when(speech.asrConfigured()).thenReturn(true);
        send(post(root), student, request).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"));
        when(model.available()).thenReturn(true);
        when(model.completeJson(anyString(), any())).thenReturn(json.createObjectNode().put("question", "你负责了哪部分？"),
                json.createObjectNode().put("question", "如何验证你的方案？"));
        when(speech.transcribeRecording(any(), anyString())).thenThrow(new IllegalStateException("transcriber unavailable"))
                .thenReturn("本人实现业务接口，用事务隔离避免脏读，并测试了持久化结果。");
        send(post(root), outsider, request).andExpect(status().isNotFound());
        var options = good(get(root + "/options"), student, null);
        assertThat(options.at("/projects/0/id").asText()).isEqualTo(project);
        JsonNode session = parse(send(post(root), student, request).andExpect(status().isCreated()));
        assertThat(session.at("/turn/text").isNull()).isTrue();
        String sessionId = session.path("id").asText(), path = root + "/" + sessionId;
        send(get(path), outsider, null).andExpect(status().isNotFound());
        String introId = session.at("/turn/id").asText(), intro = path + "/turns/" + introId;
        byte[] original = wav((byte) 99);
        send(multipart(intro + "/audio").file(audio("audio", original)), student, null).andExpect(status().isConflict());
        good(post(intro + "/start"), student, null);
        clock.advance(60);
        assertThat(good(post(intro + "/stop"), student, null).path("state").asText()).isEqualTo("AWAITING_AUDIO");
        send(multipart(intro + "/audio").file(audio("audio", original)), student, null).andExpect(status().isServiceUnavailable());
        assertThat(good(get(path), student, null).path("state").asText()).isEqualTo("AUDIO_SAVED");
        session = good(multipart(intro + "/audio").file(audio("audio", wav((byte) 11))), student, null);
        assertThat(session.at("/turn/stage").asText()).isEqualTo("PROJECT");
        good(patch("/api/admin/content/questions/{id}/enabled", content.primary()), admin, Map.of("enabled", false));
        session = answerFormal(path, student, session);
        assertThat(session.at("/turn/followUp").asBoolean()).isTrue();
        session = answerFormal(path, student, session);
        assertThat(session.at("/turn/stage").asText()).isEqualTo("TECHNICAL");
        session = answerFormal(path, student, session);
        assertThat(session.path("endReason").asText()).isEqualTo("COMPLETED");
        String reportPath = "/api/reports/sessions/" + sessionId;
        var report = waitForReport(student, reportPath);
        assertThat(report.path("answers")).hasSize(4);
        assertThat(report.at("/answers/0/timedOut").asBoolean()).isTrue();
        assertThat(report.at("/answers/3/question").asText()).isEqualTo("原题干");
        String playback = reportPath + "/audio/" + introId;
        send(get(playback), outsider, null).andExpect(status().isNotFound());
        send(get(playback), admin, null).andExpect(status().isForbidden());
        send(get(playback), student, null).andExpect(content().bytes(original));

        var second = parse(send(post(root), student, request).andExpect(status().isCreated()));
        String secondPath = root + "/" + second.path("id").asText();
        String secondTurn = secondPath + "/turns/" + second.at("/turn/id").asText();
        good(post(secondTurn + "/start"), student, null);
        good(post(secondTurn + "/stop"), student, null);
        when(speech.transcribeRecording(any(), anyString())).thenThrow(new IllegalStateException("unavailable"));
        send(multipart(secondTurn + "/audio").file(audio("audio", original)), student, null).andExpect(status().isServiceUnavailable());
        good(post(secondPath + "/end"), student, Map.of("reason", "INTERRUPTED"));
        var interrupted = waitForReport(student, "/api/reports/sessions/" + second.path("id").asText());
        assertThat(interrupted.at("/answers/0/status").asText()).isEqualTo("AUDIO_ONLY");
        assertThat(interrupted.at("/summary/totalScore").isNull()).isTrue();
        assertThat(good(get("/api/reports/history").param("mode", "FORMAL"), student, null).path("total").asInt()).isEqualTo(2);
    }

    @Test void failedReportCanBeRetriedAndDoesNotChangeAfterRuleUpdates() throws Exception {
        var student = actor(false);
        var admin = actor(true);
        var content = fixtures(admin);
        String rules = "/api/admin/evaluation-rules";
        var input = Map.of("name", "普通训练权重", "modeScope", "NORMAL", "enabled", true, "published", true,
                "weights", Map.of("completeness", 40, "accuracy", 20, "structure", 15, "relevance", 15, "expression", 10));
        send(post(rules), student, input).andExpect(status().isForbidden());
        String ruleId = good(post(rules), admin, input).path("id").asText();
        String id = createTraining(student, content, "NORMAL", List.of(content.primary())).path("id").asText();
        String training = "/api/training/sessions/" + id;
        good(post(training + "/answers"), student, Map.of("position", 0, "roundNumber", 1, "text", "事务隔离避免脏读",
                "requestKey", UUID.randomUUID().toString()));
        doThrow(new IllegalStateException("simulated report error")).doCallRealMethod().when(aggregator)
                .aggregate(anyList(), any(), anyList(), anyInt(), anyInt(), anyString());
        good(post(training + "/end"), student, cursor(0, 1));
        String reportPath = "/api/reports/sessions/" + id;
        assertThat(waitForReportState(student, reportPath, "FAILED").path("failureReason").asText()).contains("已保存");
        assertThat(good(get(training), student, null).at("/answers/0/text").asText()).isEqualTo("事务隔离避免脏读");
        var completed = good(post(reportPath + "/generate"), student, null);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.at("/rule/id").asText()).isEqualTo(ruleId);
        assertThat(completed.at("/rule/weights/completeness").asInt()).isEqualTo(40);
        good(put(rules + "/" + ruleId + "/status").param("enabled", "false").param("published", "false"), admin, null);
        assertThat(good(get(reportPath), student, null)).isEqualTo(completed);
    }

    private JsonNode answerFormal(String path, String student, JsonNode session) throws Exception {
        String turn = path + "/turns/" + session.at("/turn/id").asText();
        good(post(turn + "/start"), student, null);
        clock.advance(5);
        good(post(turn + "/stop"), student, null);
        return good(multipart(turn + "/audio").file(audio("audio", wav((byte) 1))), student, null);
    }

    private String confirmedProject(String student) throws Exception {
        byte[] pdf;
        try (var doc = new PDDocument(); var buffer = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage()); doc.save(buffer); pdf = buffer.toByteArray();
        }
        var resume = good(multipart("/api/resumes").file(new MockMultipartFile("file", "fixture.pdf", "application/pdf", pdf)), student, null);
        String path = "/api/resumes/" + resume.at("/summary/id").asText();
        var draft = good(put(path + "/parsed-data"), student, Map.of("expectedRevision", resume.at("/summary/revision").asInt(), "payload",
                Map.of("education", List.of(), "skills", List.of("Java"), "internships", List.of(), "projects", List.of(Map.of(
                        "name", "课程项目", "goal", "实现课程功能", "responsibility", "接口设计", "techStack", List.of("Java"),
                        "difficulty", "", "solution", "", "result", "", "improvement", "")))));
        good(post(path + "/confirm"), student, Map.of("expectedRevision", draft.at("/summary/revision").asInt()));
        return good(get(path + "/projects"), student, null).at("/0/id").asText();
    }

    private Contents fixtures(String admin) throws Exception {
        long job = good(post("/api/admin/content/jobs"), admin, Map.of("name", "联调岗位-" + UUID.randomUUID(), "description", "介绍",
                "requirements", "岗位要求", "interviewFocus", "面试重点", "enabled", true, "sortOrder", 0)).path("id").asLong();
        long category = good(post("/api/admin/content/categories"), admin, Map.of("jobId", job, "name", "事务", "description", "事务基础",
                "enabled", true, "sortOrder", 0)).path("id").asLong();
        var contents = new Contents(job, category, 0, 0);
        long primary = good(post("/api/admin/content/questions"), admin, question(contents, "原题干", "事务隔离避免脏读", true)).path("id").asLong();
        long similar = good(post("/api/admin/content/questions"), admin, question(contents, "同类题干", "事务回滚保证一致性", true)).path("id").asLong();
        return new Contents(job, category, primary, similar);
    }

    private Map<String, Object> question(Contents contents, String text, String points, boolean enabled) {
        return Map.of("jobId", contents.job(), "categoryId", contents.category(), "title", text, "content", text, "difficulty", "MEDIUM",
                "referencePoints", points, "sourceNote", "自动化数据", "enabled", enabled);
    }

    private JsonNode createTraining(String student, Contents contents, String mode, List<Long> questions) throws Exception {
        return good(post("/api/training/sessions"), student, Map.of("mode", mode, "roleId", contents.job(), "questionIds", questions));
    }

    private JsonNode submitAnswer(String path, String student, int round, String text, String audioId) throws Exception {
        return good(post(path + "/answers"), student, Map.of("position", 0, "roundNumber", round, "text", text, "audioId", audioId,
                "requestKey", UUID.randomUUID().toString()));
    }

    private JsonNode waitForReport(String student, String path) throws Exception {
        return waitForReportState(student, path, "COMPLETED");
    }

    private JsonNode waitForReportState(String student, String path, String state) throws Exception {
        JsonNode report = good(get(path), student, null);
        for (int attempt = 0; attempt < 50 && "PENDING".equals(report.path("status").asText()); attempt++) {
            Thread.sleep(100); report = good(get(path), student, null);
        }
        assertThat(report.path("status").asText()).isEqualTo(state);
        return report;
    }

    private Map<String, Integer> cursor(int position, int round) { return Map.of("position", position, "roundNumber", round); }

    private String actor(boolean administrator) throws Exception {
        String account = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "T9-" + UUID.randomUUID();
        var registered = good(post("/api/auth/register"), null, Map.of("account", account, "password", password, "nickname", "联调账号"));
        if (administrator) users.update(null, new UpdateWrapper<User>().eq("id", registered.path("id").asText()).set("role", "ADMIN"));
        return "Bearer " + good(post("/api/auth/login"), null, Map.of("account", account, "password", password)).path("accessToken").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String auth, Object body) throws Exception {
        if (auth != null) request.header("Authorization", auth);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    private JsonNode good(MockHttpServletRequestBuilder request, String auth, Object body) throws Exception {
        return parse(send(request, auth, body).andExpect(status().is2xxSuccessful()));
    }

    private JsonNode parse(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(jsonPath("$.code").value("OK")).andReturn().getResponse().getContentAsByteArray()).path("data");
    }

    private MockMultipartFile audio(String part, byte[] value) { return new MockMultipartFile(part, "sample.wav", "audio/wav", value); }
    private byte[] wav(byte tail) { return new byte[]{'R','I','F','F', 0, 0, 0, 0, 'W','A','V','E', tail}; }
    private record Contents(long job, long category, long primary, long similar) {}

    @TestConfiguration
    static class TimeConfiguration {
        @Bean MutableClock platformTestClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-22T03:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
