package org.ktz.faceid.onnx;

public record PoseResult(
        boolean accepted,
        double yaw,
        double pitch,
        double roll,
        double brightness,
        double blurVariance,
        String errorCode,     // null if accepted
        String errorMessage
) {
    public static PoseResult ok(double yaw, double pitch, double roll,
                                double brightness, double blur) {
        return new PoseResult(true, yaw, pitch, roll, brightness, blur, null, null);
    }
    public static PoseResult rejected(String code, String message,
                                      double yaw, double pitch, double roll,
                                      double brightness, double blur) {
        return new PoseResult(false, yaw, pitch, roll, brightness, blur, code, message);
    }
}