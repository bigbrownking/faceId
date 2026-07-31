package org.ktz.faceid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pose")
public class PoseProperties {
    // HOLD_FRONT: |yaw| <= frontYawMax
    private double frontYawMax = 12.0;
    // TURN_LEFT: yaw in [turnYawMin ... turnYawMax] negative side
    // TURN_RIGHT: yaw in [turnYawMin ... turnYawMax] positive side
    private double turnYawMin = 18.0;
    private double turnYawMax = 35.0;
    // общие ограничения качества
    private double pitchMax = 20.0;     // |pitch| допустимый
    private double rollMax = 20.0;      // |roll| допустимый
    private double minBrightness = 40.0;
    private double maxBrightness = 220.0;
    private double minBlurVariance = 50.0;  // ниже — размыто
}