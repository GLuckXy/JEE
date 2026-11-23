package com.aiinterview.config;

import java.nio.charset.StandardCharsets;
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
        boolean demo = environment.matchesProfiles("demo");
        boolean teamTest = environment.matchesProfiles("team-test");
        if (!demo && !teamTest) return;
        if ((demo && teamTest) || environment.getActiveProfiles().length > 1) {
            throw new IllegalStateException("Select only one Spring profile when using demo or team-test.");
        }
        if (environment.containsProperty("bootstrap-admin")) {
            throw new IllegalStateException("Use the test accounts supplied for the selected test mode.");
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("server.address", "127.0.0.1");
        settings.put("spring.h2.console.enabled", "false");
        settings.put("app.evaluation.ai.enabled", "false");
        settings.put("app.evaluation.ai.api-key", "");
        Path storage;
        if (demo) {
            var runId = UUID.randomUUID();
            storage = Path.of("var", "demo", runId.toString());
            settings.put("spring.datasource.url", "jdbc:h2:mem:demo-" + runId + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
            settings.put("spring.datasource.username", "sa");
            settings.put("spring.datasource.password", randomSecret());
            settings.put("spring.datasource.driver-class-name", "org.h2.Driver");
            settings.put("spring.sql.init.mode", "always");
            settings.put("spring.sql.init.continue-on-error", "false");
            settings.put("spring.sql.init.schema-locations", String.join(",",
                    "classpath:schema-h2.sql", "classpath:migrations/004_content.sql",
                    "classpath:migrations/005_training_voice.sql", "classpath:migrations/006_formal.sql",
                    "classpath:migrations/007_report.sql"));
            settings.put("spring.sql.init.data-locations", "optional:classpath:/demo/data.sql");
            settings.put("app.jwt-secret", randomSecret());
            settings.put("training.speech.provider", "none");
            settings.put("interview.ai.key", "");
        } else {
            storage = Path.of("var", "team-test", "private-files");
            settings.put("spring.sql.init.mode", "never");
            settings.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
            applyTeamTestSettings(environment, settings);
        }
        settings.put("app.storage-root", storage.toAbsolutePath().toString());
        settings.put("training.audio-dir", storage.resolve("training-audio").toAbsolutePath().toString());
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, settings) {
            @Override
            public String toString() {
                return getName();
            }
        });
    }

    private static void applyTeamTestSettings(ConfigurableEnvironment environment, Map<String, Object> settings) {
        String url = required(environment, "spring.datasource.url");
        if (!url.matches("jdbc:mysql://[a-zA-Z0-9.-]+(?::[0-9]{1,5})?/[a-z][a-z0-9_]{0,58}_test\\?sslMode=VERIFY_IDENTITY")) {
            throw invalid("spring.datasource.url", "use the test database URL format from the example");
        }
        String username = required(environment, "spring.datasource.username");
        if ("root".equalsIgnoreCase(username)) throw invalid("spring.datasource.username", "use a test account");
        String jwt = required(environment, "app.jwt-secret");
        if (jwt.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw invalid("app.jwt-secret", "at least 32 UTF-8 bytes are required");
        }
        settings.put("spring.datasource.url", url + "&connectionTimeZone=UTC");
        settings.put("spring.datasource.username", username);
        settings.put("spring.datasource.password", required(environment, "spring.datasource.password"));
        settings.put("app.jwt-secret", jwt);
        settings.put("training.speech.provider", "isi");
        settings.put("training.speech.isi-access-key-id", required(environment, "training.speech.isi-access-key-id"));
        settings.put("training.speech.isi-access-key-secret", required(environment, "training.speech.isi-access-key-secret"));
        settings.put("training.speech.isi-app-key", required(environment, "training.speech.isi-app-key"));
        settings.put("interview.ai.key", required(environment, "interview.ai.key"));
        settings.put("interview.ai.url", "https://api.deepseek.com/chat/completions");
    }

    private static String required(ConfigurableEnvironment environment, String property) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException error) {
            throw invalid(property, "unresolved value");
        }
        if (value == null || value.isBlank()) throw invalid(property, "a value is required");
        return value;
    }

    private static IllegalStateException invalid(String property, String reason) {
        return new IllegalStateException("Invalid team-test field " + property + " (" + reason
                + "). Check backend/config/team-test/application.yml.");
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
