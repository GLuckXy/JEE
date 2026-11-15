package com.aiinterview;

import com.aiinterview.auth.CurrentUser;
import com.aiinterview.common.BusinessException;
import com.aiinterview.resume.ResumeQueryService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** Opt-in, read-only verification against existing MySQL flow records; never creates schema or changes rows. */
@EnabledIfEnvironmentVariable(named="VERIFY_MYSQL", matches="true")
@SpringBootTest(properties={"spring.sql.init.mode=never", "springdoc.api-docs.enabled=false"})
class MySqlTrainingQueryVerificationTest {
    @Autowired ResumeQueryService query;
    @Autowired ObjectMapper json;
    private JsonNode report;
    @BeforeEach void setup() throws Exception {
        report=json.readTree(Files.readString(Path.of(System.getenv("VERIFY_FLOW_REPORT"))));
        assertThat(report.path("success").asBoolean()).isTrue();
        authenticate(0,"STUDENT");
    }
    private void authenticate(int index,String role) {
        JsonNode account=report.path("accounts").get(index);
        // Simulate the trusted authenticated principal supplied by the already verified HTTP security chain.
        var principal=new CurrentUser(account.path("id").asText(),account.path("account").asText(),role,"verification");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void readsCurrentConfirmedVersionAndProjectSnapshot() {
        var resume=query.getConfirmedResume(report.path("resumeId").asText());
        var project=query.getConfirmedProject(report.path("resumeId").asText(),report.path("currentProjectId").asText());
        assertThat(resume.version()).isEqualTo(2);
        assertThat(project.version()).isEqualTo(2);
        assertThat(project.responsibility()).isEqualTo("更新后的本人职责");
        assertThat(resume.payload().projects().getFirst().responsibility()).isEqualTo(project.responsibility());
    }
    @Test void rejectsOtherStudentAndAdministratorPrivateReads() {
        for(String role:List.of("STUDENT","ADMIN")) {
            authenticate(1,role);
            assertThatThrownBy(()->query.getConfirmedResume(report.path("resumeId").asText())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->query.getConfirmedProject(report.path("resumeId").asText(),report.path("currentProjectId").asText()))
                    .isInstanceOf(BusinessException.class);
        }
    }
    @Test void rejectsHistoricalProjectAndCrossResumeCombination() {
        assertThatThrownBy(()->query.getConfirmedProject(report.path("resumeId").asText(),report.path("projectId").asText()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->query.getConfirmedProject(report.path("manualResumeId").asText(),report.path("currentProjectId").asText()))
                .isInstanceOf(BusinessException.class);
    }
    @Test void acceptsManuallyConfirmedResumeAfterParsingFailed() {
        var resume=query.getConfirmedResume(report.path("manualResumeId").asText());
        assertThat(resume.version()).isEqualTo(1);
        assertThat(resume.payload().projects().getFirst().responsibility()).isEqualTo("本人实现测试模块");
    }
    @Test void rejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(()->query.getConfirmedResume(report.path("resumeId").asText())).isInstanceOf(BusinessException.class);
    }
}
