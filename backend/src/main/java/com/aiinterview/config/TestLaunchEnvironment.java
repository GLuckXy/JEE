package com.aiinterview.config;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public final class TestLaunchEnvironment implements EnvironmentPostProcessor, Ordered {
    static final String PROPERTY_SOURCE = "localTestLaunch";

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.matchesProfiles("demo")) return;
        if (environment.getActiveProfiles().length > 1) {
            throw new IllegalStateException("Select only one Spring profile when using demo.");
        }
        if (environment.containsProperty("bootstrap-admin")) {
            throw new IllegalStateException("Administrator bootstrap is unavailable in demo mode.");
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("server.address", "127.0.0.1");
        settings.put("spring.h2.console.enabled", "false");
        settings.put("app.evaluation.ai.enabled", "false");
        settings.put("app.evaluation.ai.api-key", "");
        var runId = UUID.randomUUID();
        var storage = Path.of("var", "demo", runId.toString());
        settings.put("spring.datasource.url", "jdbc:h2:mem:demo-" + runId + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        settings.put("spring.datasource.username", "sa");
        settings.put("spring.datasource.password", randomSecret());
        settings.put("spring.datasource.driver-class-name", "org.h2.Driver");
        settings.put("spring.sql.init.mode", "always");
        settings.put("spring.sql.init.continue-on-error", "false");
        settings.put("spring.sql.init.schema-locations", String.join(",",
                "classpath:schema-h2.sql", "classpath:migrations/004_content.sql",
                "classpath:migrations/005_training_voice.sql", "classpath:migrations/006_formal.sql",
                "classpath:migrations/007_report.sql", "classpath:migrations/008_operation_log.sql"));
        settings.put("spring.sql.init.data-locations", "optional:classpath:/demo/data.sql");
        settings.put("app.jwt-secret", randomSecret());
        settings.put("training.speech.provider", "none");
        settings.put("interview.ai.key", "");
        settings.put("app.storage-root", storage.toAbsolutePath().toString());
        settings.put("training.audio-dir", storage.resolve("training-audio").toAbsolutePath().toString());
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, settings) {
            @Override
            public String toString() {
                return getName();
            }
        });
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
