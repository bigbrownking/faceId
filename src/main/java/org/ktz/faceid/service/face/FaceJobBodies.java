package org.ktz.faceid.service.face;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.config.LivenessMode;
import org.ktz.faceid.config.MatchingProperties;
import org.ktz.faceid.domain.reference.FaceReferenceSet;
import org.ktz.faceid.onnx.FaceEngine;
import org.ktz.faceid.service.challenge.ChallengeService;
import org.ktz.faceid.service.job.JobRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Bodies executed inside async jobs. Each returns the result map stored on the job.
 * Frames are pre-read into memory (action -> bytes) before the async body runs.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(MatchingProperties.class)
public class FaceJobBodies {

    private final FrameAnalyzer frameAnalyzer;
    private final ConsistencyChecker consistencyChecker;
    private final ReferenceSetService referenceSetService;
    private final ChallengeService challengeService;
    private final MatchingProperties matching;

    @Value("${face.liveness-mode}") private String livenessModeRaw;
    @Value("${face.embedding-version}") private String embeddingVersion;
    @Value("${face.liveness-version}") private String livenessVersion;

    private LivenessMode livenessMode() {
        return LivenessMode.valueOf(livenessModeRaw.toUpperCase());
    }

    /** PREPARE_REFERENCE_SET / ENROLL_REFERENCE_SET: 3 poses, build a new active set. */
    public Map<String, Object> buildReferenceSet(Long userId,
                                                 UUID challengeId,
                                                 boolean isEnroll,
                                                 LinkedHashMap<String, byte[]> frames) throws Exception {
        boolean hasExisting = referenceSetService.activeSet(userId).isPresent();
        if (isEnroll && hasExisting && !referenceSetService.hasTrustedFront(userId)) {
            throw new JobRejectedException("IU011", "Trusted FRONT reference is required");
        }

        Map<String, float[]> poseEmbeddings = new LinkedHashMap<>();
        List<float[]> all = new ArrayList<>();
        boolean livenessPassed = true;   // все кадры должны быть live
        double minLive = 1.0;            // худший live-скор по кадрам

        for (Map.Entry<String, byte[]> f : frames.entrySet()) {
            String action = f.getKey();                    // HOLD_FRONT / TURN_LEFT / TURN_RIGHT
            FrameAnalyzer.AnalyzedFrame af = frameAnalyzer.analyze(action, f.getValue());
            String pose = actionToPose(action);
            poseEmbeddings.put(pose, af.embedding());
            all.add(af.embedding());
            minLive = Math.min(minLive, af.liveScore());
            if (!af.live()) livenessPassed = false;
        }

        // all frames must be the same person
        consistencyChecker.ensureSamePerson(all);

        // liveness gate: only blocks in ENFORCE mode; SHADOW logs but never blocks.
        // Enrolling a spoofed reference would be worse than a spoofed verify,
        // so we reject on liveness here when enforced.
        if (livenessMode() == LivenessMode.ENFORCE && !livenessPassed) {
            throw new JobRejectedException("LIVENESS_FAILED",
                    "Liveness check failed during enrollment");
        }

        if (isEnroll && hasExisting) {
            float[] existingFront = referenceSetService.trustedFrontEmbedding(userId);
            float[] newFront = poseEmbeddings.get("FRONT");
            if (newFront != null) {
                float sim = FaceEngine.cosine(existingFront, newFront);
                if (sim < matching.getCosineThreshold()) {
                    throw new JobRejectedException("MATCH_FAILED",
                            "New FRONT does not match trusted FRONT");
                }
            }
        }

        FaceReferenceSet set = referenceSetService.createActiveSet(userId, poseEmbeddings);
        if (challengeId != null) challengeService.markConsumed(challengeId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prepared", !isEnroll);          // reference-set => prepared=true; enroll => false
        result.put("referenceSetId", set.getId().toString());
        result.put("embeddingVersion", embeddingVersion);
        result.put("livenessPassed", livenessPassed);
        result.put("liveScore", minLive);
        result.put("livenessMode", livenessMode().name());
        return result;
    }

    /** VERIFY: 2 frames (FRONT + one turn), match FRONT against trusted reference. */
    public Map<String, Object> verify(Long userId,
                                      UUID challengeId,
                                      LinkedHashMap<String, byte[]> frames) throws Exception {
        if (!referenceSetService.hasTrustedFront(userId)) {
            throw new JobRejectedException("IU011", "Face ID enabled but no FRONT reference");
        }

        // analyze both frames (validates poses + liveness)
        List<float[]> all = new ArrayList<>();
        float[] frontEmb = null;
        boolean livenessPassed = true;   // все кадры должны быть live
        double minLive = 1.0;            // худший live-скор по кадрам

        for (Map.Entry<String, byte[]> f : frames.entrySet()) {
            String action = f.getKey();
            FrameAnalyzer.AnalyzedFrame af = frameAnalyzer.analyze(action, f.getValue());
            all.add(af.embedding());
            minLive = Math.min(minLive, af.liveScore());
            if (!af.live()) livenessPassed = false;
            if ("HOLD_FRONT".equals(action)) frontEmb = af.embedding();
        }
        if (frontEmb == null) {
            throw new JobRejectedException("POSE_NOT_CONFIRMED", "No FRONT frame in verify");
        }

        // all frames must be the same person
        consistencyChecker.ensureSamePerson(all);

        // match FRONT probe against trusted FRONT reference
        float[] reference = referenceSetService.trustedFrontEmbedding(userId);
        float sim = FaceEngine.cosine(frontEmb, reference);
        boolean matched = sim >= matching.getCosineThreshold();

        // liveness gate: only blocks in ENFORCE mode; SHADOW logs but never blocks
        if (livenessMode() == LivenessMode.ENFORCE && !livenessPassed) {
            matched = false;
        }

        if (matched && challengeId != null) {
            challengeService.markConsumed(challengeId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("similarity", sim);
        result.put("livenessPassed", livenessPassed);
        result.put("liveScore", minLive);
        result.put("livenessMode", livenessMode().name());
        result.put("challengeId", challengeId == null ? null : challengeId.toString());
        result.put("embeddingVersion", embeddingVersion);
        result.put("livenessVersion", livenessVersion);
        return result;
    }

    private String actionToPose(String action) {
        return switch (action) {
            case "HOLD_FRONT" -> "FRONT";
            case "TURN_LEFT"  -> "YAW_LEFT";
            case "TURN_RIGHT" -> "YAW_RIGHT";
            default -> throw new JobRejectedException("POSE_NOT_CONFIRMED", "Unknown action " + action);
        };
    }
}