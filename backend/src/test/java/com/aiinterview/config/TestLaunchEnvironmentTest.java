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
    void demoCannotBeCombinedWithAnotherProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("demo", "test");
        assertThatThrownBy(() -> new TestLaunchEnvironment().postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only one Spring profile");
    }
}
