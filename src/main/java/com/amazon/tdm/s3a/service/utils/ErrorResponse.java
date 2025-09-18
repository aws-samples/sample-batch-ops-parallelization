package com.amazon.tdm.s3a.service.utils;

import lombok.Data;

@Data
public class ErrorResponse {
    private final String message;
    private final int statusCode;
    private final String errorId;
    private final long timestamp;

    public ErrorResponse(String message, int statusCode, String errorId) {
        this.message = message;
        this.statusCode = statusCode;
        this.errorId = errorId;
        this.timestamp = System.currentTimeMillis();
    }
}