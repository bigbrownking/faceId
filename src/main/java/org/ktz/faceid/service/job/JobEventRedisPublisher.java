package org.ktz.faceid.service.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ktz.faceid.domain.job.FaceJob;
import org.ktz.faceid.dto.job.JobEventMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes job status changes to a Redis channel. The orchestrator subscribes
 * and forwards the event to the end user over its own WebSocket.
 *
 * The message carries routing info (userId / jobToken) so the orchestrator knows
 * which client to push to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobEventRedisPublisher {

    public static final String CHANNEL = "face-job-events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void publish(FaceJob job) {
        try {
            JobEventMessage msg = JobEventMessage.from(job);
            String payload = objectMapper.writeValueAsString(msg);
            redis.convertAndSend(CHANNEL, payload);
            log.debug("Published job {} status={} to Redis", job.getJobId(), job.getStatus());
        } catch (Exception e) {
            log.warn("Failed to publish job event {}: {}", job.getJobId(), e.getMessage());
        }
    }
}