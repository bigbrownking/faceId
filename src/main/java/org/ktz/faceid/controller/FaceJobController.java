package org.ktz.faceid.controller;

import lombok.RequiredArgsConstructor;
import org.ktz.faceid.domain.job.FaceJob;
import org.ktz.faceid.dto.job.JobView;
import org.ktz.faceid.service.job.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/face-id/jobs")
@RequiredArgsConstructor
public class FaceJobController {

    private final JobService jobService;

    /**
     * Poll job status.
     * Anonymous (registration): pass X-Face-Job-Token header.
     * Authenticated: userId resolved from the auth token (wired in later layer).
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobView> get(
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-Face-Job-Token", required = false) String jobToken,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        FaceJob job = jobService.getForAccess(jobId, userId, jobToken);
        return ResponseEntity.ok(JobView.from(job));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-Face-Job-Token", required = false) String jobToken,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        // ensure caller can access this job before cancelling
        jobService.getForAccess(jobId, userId, jobToken);
        boolean cancelled = jobService.cancel(jobId);
        return cancelled ? ResponseEntity.ok().build()
                : ResponseEntity.status(409).build();
    }
}