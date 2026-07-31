package org.ktz.faceid.service.challenge;

import lombok.Getter;

@Getter
public class ChallengeException extends RuntimeException {
    private final int httpStatus;
    public ChallengeException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }
}