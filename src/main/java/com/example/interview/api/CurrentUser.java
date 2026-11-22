package com.example.interview.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Implement with the platform's authenticated principal. */
public interface CurrentUser {
    String requireId(HttpServletRequest request, HttpServletResponse response);
}
