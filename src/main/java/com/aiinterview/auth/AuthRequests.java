package com.aiinterview.auth;

import jakarta.validation.constraints.*;

public final class AuthRequests {
    private AuthRequests() {}
    public record Register(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_]{8,16}", message = "账号为 8–16 位英文、数字或下划线") String account,
            @NotNull @Size(min = 8, max = 72) @Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).+", message = "密码至少包含字母和数字") String password,
            @NotBlank @Size(max = 64) String nickname) {}
    public record Login(@NotBlank @Size(max = 16) String account, @NotBlank @Size(max = 72) String password) {}
}
