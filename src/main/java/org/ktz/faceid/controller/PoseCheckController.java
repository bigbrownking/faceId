package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.dto.pose.PoseCheckResponse;
import org.ktz.faceid.onnx.FaceAnalysis;
import org.ktz.faceid.onnx.FaceEngine;
import org.ktz.faceid.onnx.PoseEstimator;
import org.ktz.faceid.onnx.PoseResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/face-id/test")
@RequiredArgsConstructor
public class PoseCheckController {

    private final FaceEngine faceEngine;
    private final PoseEstimator poseEstimator;

    /**
     * Pose preview for UX. Public if enabled.
     * multipart part name: "frame".
     */
    @PostMapping("/pose-check")
    public ResponseEntity<PoseCheckResponse> poseCheck(
            @RequestParam("requestedAction") String requestedAction,
            @RequestPart("frame") MultipartFile frame) throws Exception {

        FaceAnalysis fa = faceEngine.analyze(frame.getBytes(), false);
        if (!fa.faceFound()) {
            return ResponseEntity.ok(new PoseCheckResponse(
                    false, 0, 0, 0, 0, 0, "NO_FACE", "No face detected"));
        }

        PoseResult pose = poseEstimator.checkAction(requestedAction, fa.pose());
        return ResponseEntity.ok(new PoseCheckResponse(
                pose.accepted(), pose.yaw(), pose.pitch(), pose.roll(),
                pose.brightness(), pose.blurVariance(),
                pose.errorCode(), pose.errorMessage()));
    }
}