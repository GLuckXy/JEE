package com.aiinterview.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLaunchEnvironmentTest {
    @Test
    void demoOverridesDatabaseCloudAndBindAddressAndUsesItsOwnSigningMaterial() {
        var demo = new MockEnvironment()
                .withProperty("server.address", "0.0.0.0")
                .withProperty("spring.datasource.url", "jdbc:mysql://example.invalid/wrong")
                .withProperty("training.speech.provider", "isi")
                .withProperty("interview.ai.key", "synthetic-placeholder")
                .withProperty("app.evaluation.ai.enabled", "true");
        demo.setActiveProfiles("demo");
        var processor = new TestLaunchEnvironment();
        processor.postProcessEnvironment(demo, new SpringApplication());

        assertThat(demo.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(demo.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:demo-");
        assertThat(demo.getProperty("training.speech.provider")).isEqualTo("none");
        assertThat(demo.getProperty("interview.ai.key")).isEmpty();
        assertThat(demo.getProperty("app.evaluation.ai.enabled")).isEqualTo("false");
        assertThat(demo.getProperty("app.jwt-secret")).matches("[0-9a-f]{64}");

        var next = new MockEnvironment();
        next.setActiveProfiles("demo");
        processor.postProcessEnvironment(next, new SpringApplication());
        assertThat(next.getProperty("app.jwt-secret")).isNotEqualTo(demo.getProperty("app.jwt-secret"));
        assertThat(next.getProperty("spring.datasource.url")).isNotEqualTo(demo.getProperty("spring.datasource.url"));
    }

    @Test
    void testLaunchCannotBeCombinedWithAnotherProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("demo", "team-test");
        assertThatThrownBy(() -> new TestLaunchEnvironment().postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only one Spring profile");
    }

    @Test
    void teamTestUsesExternalPropertiesAndKeepsMysqlInitializationDisabled() {
        var environment = teamTest()
                .withProperty("spring.sql.init.mode", "always")
                .withProperty("interview.ai.url", "https://example.invalid/");
        new TestLaunchEnvironment().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://localhost/mianxu_test?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("synthetic-db-password");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getProperty("training.speech.provider")).isEqualTo("isi");
        assertThat(environment.getProperty("interview.ai.url")).isEqualTo("https://api.deepseek.com/chat/completions");
    }

    @Test
    void teamTestReportsTheMissingFieldAndRejectsAnUnverifiedDatabaseUrl() {
        var processor = new TestLaunchEnvironment();
        assertThatThrownBy(() -> processor.postProcessEnvironment(
                teamTest().withProperty("interview.ai.key", ""), new SpringApplication()))
                .hasMessageContaining("interview.ai.key")
                .hasMessageNotContaining("synthetic-db-password");
        assertThatThrownBy(() -> processor.postProcessEnvironment(
                teamTest().withProperty("spring.datasource.url", "jdbc:mysql://localhost/mianxu_test?sslMode=REQUIRED"),
                new SpringApplication()))
                .hasMessageContaining("spring.datasource.url");
    }

    private static MockEnvironment teamTest() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost/mianxu_test?sslMode=VERIFY_IDENTITY")
                .withProperty("spring.datasource.username", "synthetic-user")
                .withProperty("spring.datasource.password", "synthetic-db-password")
                .withProperty("app.jwt-secret", "synthetic-signing-material-for-unit-test")
                .withProperty("training.speech.isi-access-key-id", "synthetic-access-id")
                .withProperty("training.speech.isi-access-key-secret", "synthetic-access-secret")
                .withProperty("training.speech.isi-app-key", "synthetic-app-key")
                .withProperty("interview.ai.key", "synthetic-ai-key");
        environment.setActiveProfiles("team-test");
        return environment;
    }
}
