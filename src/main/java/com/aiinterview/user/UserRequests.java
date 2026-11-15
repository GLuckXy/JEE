package com.aiinterview.user;

import jakarta.validation.constraints.*;

public final class UserRequests {
    private UserRequests() {}
    public record Profile(@NotBlank @Size(max=64) String nickname, @Email @Size(max=254) String email,
            @Pattern(regexp="[+0-9 ()-]{0,32}") String phone, @Size(max=128) String school, @Size(max=128) String major) {}
    public record Status(@NotNull @Pattern(regexp="ACTIVE|DISABLED") String status) {}
    public record AdminProfile(@NotBlank @Size(max=64) String nickname) {}
}
