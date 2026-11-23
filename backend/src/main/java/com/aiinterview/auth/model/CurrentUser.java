package com.aiinterview.auth.model;

public record CurrentUser(String id, String account, String role, String sessionId) {}
