package com.aiinterview.auth;

import com.aiinterview.common.ApiResponse;
import com.aiinterview.user.UserView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UserAccessService access;
    public AuthController(AuthService auth, UserAccessService access) { this.auth=auth; this.access=access; }
    @PostMapping("/register") public ApiResponse<UserView> register(@Valid @RequestBody AuthRequests.Register request) { return ApiResponse.ok(auth.register(request)); }
    @PostMapping("/login") public ApiResponse<AuthService.TokenResult> login(@Valid @RequestBody AuthRequests.Login request) { return ApiResponse.ok(auth.login(request)); }
    @PostMapping("/logout") public ApiResponse<Void> logout() { auth.logout(access.getCurrentUser()); return ApiResponse.ok(null); }
}
