package org.ktz.faceid.service.job;

import lombok.Getter;

@Getter
public class JobRejectedException extends RuntimeException {
    private final String code;
    public JobRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }
}