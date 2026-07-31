package org.ktz.faceid.onnx;

public record FaceAnalysis(
        boolean faceFound,
        float detectionScore,
        float[] landmarks,
        float[] embedding,
        PoseResult pose,
        double liveScore,
        boolean live
) {
    public static FaceAnalysis noFace() {
        return new FaceAnalysis(false, -1f, null, null, null, 0, false);
    }
}