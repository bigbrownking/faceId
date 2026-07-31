package org.ktz.faceid;

import nu.pattern.OpenCV;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FaceIdApplication {

    public static void main(String[] args) {
        OpenCV.loadLocally();
        SpringApplication.run(FaceIdApplication.class, args);
    }

}
