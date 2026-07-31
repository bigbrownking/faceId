package org.ktz.faceid.controller;

import org.ktz.faceid.service.challenge.ChallengeException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FaceExceptionHandler {

    @ExceptionHandler(ChallengeException.class)
    public ResponseEntity<Map<String, Object>> handleChallenge(ChallengeException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(Map.of("error", e.getMessage(), "status", e.getHttpStatus()));
    }
}