package org.ktz.faceid.service.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.domain.job.JobStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobProcessor {

    private final JobService jobService;

    /**
     * Run a job body asynchronously on the "faceJobs" executor.
     * The body returns the result map on success or throws to fail the job.
     */
    @Async("faceJobExecutor")
    public void run(UUID jobId, Supplier<Map<String, Object>> body) {
        try {
            jobService.markRunning(jobId);
            Map<String, Object> result = body.get();
            jobService.succeed(jobId, result);
            log.info("Job {} SUCCEEDED", jobId);
        } catch (JobRejectedException e) {
            jobService.fail(jobId, JobStatus.REJECTED, e.getCode(), e.getMessage());
            log.warn("Job {} REJECTED: {}", jobId, e.getMessage());
        } catch (Throwable e) {
            jobService.fail(jobId, JobStatus.FAILED, "INTERNAL", e.getMessage());
            log.error("Job {} FAILED", jobId, e);
        }
    }
}