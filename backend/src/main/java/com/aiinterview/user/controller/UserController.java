package com.aiinterview.user.controller;

import com.aiinterview.common.ApiResponse;
import com.aiinterview.common.PageResult;
import com.aiinterview.user.dto.UserRequests;
import com.aiinterview.user.dto.UserView;
import com.aiinterview.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users=users; }
    @GetMapping("/api/users/me") public ApiResponse<UserView> me() { return ApiResponse.ok(users.me()); }
    @PutMapping("/api/users/me") public ApiResponse<UserView> update(@Valid @RequestBody UserRequests.Profile p) { return ApiResponse.ok(users.updateMe(p)); }
    @GetMapping("/api/admin/users") public ApiResponse<PageResult<UserView>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return ApiResponse.ok(users.list(page,size)); }
    @GetMapping("/api/admin/users/{id}") public ApiResponse<UserView> detail(@PathVariable String id) { return ApiResponse.ok(users.detail(id)); }
    @PutMapping("/api/admin/users/{id}/status") public ApiResponse<UserView> status(@PathVariable String id,@Valid @RequestBody UserRequests.Status p) { return ApiResponse.ok(users.setStatus(id,p)); }
    @PutMapping("/api/admin/users/{id}") public ApiResponse<UserView> adminUpdate(@PathVariable String id,@Valid @RequestBody UserRequests.AdminProfile p) { return ApiResponse.ok(users.adminUpdate(id,p)); }
}
