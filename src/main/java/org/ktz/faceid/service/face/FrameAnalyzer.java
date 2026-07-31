package org.ktz.faceid.service.face;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.onnx.FaceAnalysis;
import org.ktz.faceid.onnx.FaceEngine;
import org.ktz.faceid.onnx.PoseEstimator;
import org.ktz.faceid.onnx.PoseResult;
import org.ktz.faceid.service.job.JobRejectedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FrameAnalyzer {

    private final FaceEngine faceEngine;
    private final PoseEstimator poseEstimator;

    public record AnalyzedFrame(String action, float[] embedding, PoseResult pose,
                                double liveScore, boolean live) {}
    /**
     * Analyze one frame for a requested action.
     * Throws JobRejectedException with doc-aligned codes on failure.
     */
    public AnalyzedFrame analyze(String requestedAction, byte[] imageBytes) throws Exception {
        FaceAnalysis fa = faceEngine.analyze(imageBytes, true);
        if (!fa.faceFound()) {
            throw new JobRejectedException("NO_FACE", "No face detected for " + requestedAction);
        }
        PoseResult pose = poseEstimator.checkAction(requestedAction, fa.pose());
        if (!pose.accepted()) {
            throw new JobRejectedException(
                    pose.errorCode() == null ? "POSE_NOT_CONFIRMED" : pose.errorCode(),
                    pose.errorMessage() == null ? "Pose not confirmed" : pose.errorMessage());
        }
        if (fa.embedding() == null) {
            throw new JobRejectedException("INTERNAL", "Embedding failed for " + requestedAction);
        }
        return new AnalyzedFrame(requestedAction, fa.embedding(), pose, fa.liveScore(), fa.live());
    }
}