package org.ktz.faceid.dto.pose;

public record PoseCheckResponse(
        boolean accepted,
        double yaw,
        double pitch,
        double roll,
        double brightness,
        double blurVariance,
        String errorCode,
        String errorMessage
) {}