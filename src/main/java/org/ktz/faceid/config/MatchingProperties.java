package org.ktz.faceid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "matching")
public class MatchingProperties {
    private String thresholdVersion = "v1";
    private double cosineThreshold = 0.42;
    private double stepUpBand = 0.05;
}