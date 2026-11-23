package com.aiinterview.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String jwtSecret, @NotNull Duration tokenTtl,
        @NotNull Path storageRoot, @Min(1) long maxUploadBytes,
        @NotEmpty Set<String> allowedExtensions, @Min(1) int maxTextChars) {
    public AppProperties {
        if (jwtSecret != null && jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("APP_JWT_SECRET must contain at least 32 UTF-8 bytes");
        if (tokenTtl != null && (tokenTtl.isZero() || tokenTtl.isNegative()))
            throw new IllegalArgumentException("APP_TOKEN_TTL must be positive");
    }
}
