package org.ktz.faceid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "challenge")
public class ChallengeProperties {
    private long ttlSeconds = 90;
    private int maxAttempts = 5;
}