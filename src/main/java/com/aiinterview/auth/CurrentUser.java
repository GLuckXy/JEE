package com.aiinterview.auth;

public record CurrentUser(String id, String account, String role, String sessionId) {}
