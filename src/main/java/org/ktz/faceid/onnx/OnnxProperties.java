package org.ktz.faceid.onnx;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "onnx")
public class OnnxProperties {
    private String modelDir;
    private String detector;
    private String recognizer;
    private String detectorUrl;
    private String recognizerUrl;
    private int intraOpThreads = 2;
    private String antispoof;
    private String antispoofUrl;
    private double liveThreshold = 0.7;
}
