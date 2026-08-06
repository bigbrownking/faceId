package org.ktz.faceid.controller;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ktz.faceid.domain.job.*;
import org.ktz.faceid.dto.frame.FrameMetadata;
import org.ktz.faceid.service.face.FaceJobBodies;
import org.ktz.faceid.service.job.JobProcessor;
import org.ktz.faceid.service.job.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/face-id/jobs")
@RequiredArgsConstructor
public class FaceJobsController {

    private final JobService jobService;
    private final JobProcessor jobProcessor;
    private final FaceJobBodies bodies;
    private final ObjectMapper objectMapper;

    /** Registration: anonymous, 3 poses, prepares a reference set. Returns jobToken. */
    @PostMapping(value = "/reference-set", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> referenceSet(
            @RequestPart("metadata") String metadataJson,
            @RequestPart("HOLD_FRONT") MultipartFile front,
            @RequestPart("TURN_LEFT") MultipartFile left,
            @RequestPart("TURN_RIGHT") MultipartFile right,
            @RequestAttribute(value = "userId", required = false) Long userId) throws Exception {

        FrameMetadata meta = objectMapper.readValue(metadataJson, FrameMetadata.class);
        LinkedHashMap<String, byte[]> frames = readFrames(front, left, right);

        // anonymous during registration -> userId may be null; a pending user id
        // is bound later at /auth/register. Here we allow null and store on job.
        FaceJob job = jobService.create(JobType.PREPARE_REFERENCE_SET, userId,
                meta.challengeId(), Instant.now().plus(Duration.ofMinutes(10)));

        jobProcessor.run(job.getJobId(), () -> {
            try {
                return bodies.buildReferenceSet(job.getJobId(), userId, meta.challengeId(), false, frames);
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.getJobId().toString(),
                "status", job.getStatus().name(),
                "jobToken", job.getJobToken()));
    }

    /** Enroll missing poses: authenticated, 3 poses, requires trusted FRONT + challenge. */
    @PostMapping(value = "/enroll-set", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> enrollSet(
            @RequestPart("metadata") String metadataJson,
            @RequestPart("HOLD_FRONT") MultipartFile front,
            @RequestPart("TURN_LEFT") MultipartFile left,
            @RequestPart("TURN_RIGHT") MultipartFile right,
            @RequestAttribute("userId") Long userId) throws Exception {

        FrameMetadata meta = objectMapper.readValue(metadataJson, FrameMetadata.class);
        LinkedHashMap<String, byte[]> frames = readFrames(front, left, right);

        FaceJob job = jobService.create(JobType.ENROLL_REFERENCE_SET, userId,
                meta.challengeId(), Instant.now().plus(Duration.ofMinutes(10)));

        jobProcessor.run(job.getJobId(), () -> {
            try {
                return bodies.buildReferenceSet(job.getJobId(), userId, meta.challengeId(), true, frames);
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.getJobId().toString(),
                "status", job.getStatus().name()));
    }

    /** Verify: authenticated (or pre-auth), 2 frames FRONT + frame. */
    @PostMapping(value = "/verify", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestPart("metadata") String metadataJson,
            @RequestPart("HOLD_FRONT") MultipartFile front,
            @RequestPart("frame") MultipartFile secondFrame,
            @RequestAttribute("userId") Long userId) throws Exception {

        FrameMetadata meta = objectMapper.readValue(metadataJson, FrameMetadata.class);

        // the second frame's action comes from metadata.frames[1].requestedAction
        String secondAction = meta.frames().get(1).requestedAction();

        LinkedHashMap<String, byte[]> frames = new LinkedHashMap<>();
        frames.put("HOLD_FRONT", front.getBytes());
        frames.put(secondAction, secondFrame.getBytes());

        FaceJob job = jobService.create(JobType.VERIFY, userId,
                meta.challengeId(), Instant.now().plus(Duration.ofMinutes(5)));

        jobProcessor.run(job.getJobId(), () -> {
            try {
                return bodies.verify(job.getJobId(), userId, meta.challengeId(), frames);
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.getJobId().toString(),
                "status", job.getStatus().name()));
    }

    private LinkedHashMap<String, byte[]> readFrames(MultipartFile front, MultipartFile left,
                                                     MultipartFile right) throws Exception {
        LinkedHashMap<String, byte[]> frames = new LinkedHashMap<>();
        frames.put("HOLD_FRONT", front.getBytes());
        frames.put("TURN_LEFT", left.getBytes());
        frames.put("TURN_RIGHT", right.getBytes());
        return frames;
    }
}