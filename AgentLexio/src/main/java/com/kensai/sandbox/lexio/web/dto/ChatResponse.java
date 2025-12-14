package com.kensai.sandbox.lexio.web.dto;

public record ChatResponse(
    String message,
    boolean success,
    String error
) {}
