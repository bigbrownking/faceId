package org.ktz.faceid.onnx;

import ai.onnxruntime.OrtEnvironment;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;

/**
 * Central face engine. Model version strings are exposed so they can be
 * persisted with each enrollment (recognition_model_version etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(OnnxProperties.class)
public class FaceEngine {
    private final PoseEstimator poseEstimator;
    private final OnnxProperties props;
    private OrtEnvironment env;
    private ScrfdDetector detector;
    private ArcFaceRecognizer recognizer;
    private AntiSpoofDetector antiSpoof;
    private double liveThreshold;

    @PostConstruct
    public void init() throws Exception {
        Path dir = Paths.get(props.getModelDir());
        Files.createDirectories(dir);
        Path det = ensure(dir.resolve(props.getDetector()), props.getDetectorUrl());
        Path rec = ensure(dir.resolve(props.getRecognizer()), props.getRecognizerUrl());

        env = OrtEnvironment.getEnvironment();
        detector = new ScrfdDetector(env, Files.readAllBytes(det), props.getIntraOpThreads());
        recognizer = new ArcFaceRecognizer(env, Files.readAllBytes(rec), props.getIntraOpThreads());
        log.info("FaceEngine ready. detector={}, recognizer={}", det, rec);
        Path anti = ensure(dir.resolve(props.getAntispoof()), props.getAntispoofUrl());
        antiSpoof = new AntiSpoofDetector(env, Files.readAllBytes(anti), props.getIntraOpThreads());
        liveThreshold = props.getLiveThreshold();
        log.info("AntiSpoof model ready: {}", anti);
    }

    private Path ensure(Path target, String url) throws Exception {
        if (Files.exists(target) && Files.size(target) > 0) return target;
        log.info("Model {} missing, downloading from {}", target.getFileName(), url);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new IllegalStateException("Download failed: HTTP " + resp.statusCode());
        try (InputStream in = resp.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** Decode JPEG/PNG bytes and produce an L2-normalized embedding, or null if no face. */
    public float[] embedFromImageBytes(byte[] imageBytes) throws Exception {
        Mat img = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
        if (img.empty()) throw new IllegalArgumentException("Cannot decode image");
        try {
            ScrfdDetector.Face face = detector.detectBest(img);
            if (face == null) return null;
            return recognizer.embed(img, face.landmarks());
        } finally {
            img.release();
        }
    }

    /** Detection quality proxy = detector confidence, or -1 if no face. */
    public float detectionScore(byte[] imageBytes) throws Exception {
        Mat img = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
        try {
            ScrfdDetector.Face face = detector.detectBest(img);
            return face == null ? -1f : face.score();
        } finally {
            img.release();
        }
    }

    public static float cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return (float) dot; // already L2-normalized
    }
    public FaceAnalysis analyze(byte[] imageBytes, boolean withEmbedding) throws Exception {
        Mat img = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
        if (img.empty()) throw new IllegalArgumentException("Cannot decode image");
        try {
            ScrfdDetector.Face face = detector.detectBest(img);
            if (face == null) return FaceAnalysis.noFace();

            PoseResult pose = poseEstimator.estimate(face.landmarks(), img);

            double live = antiSpoof.liveScore(img, face.x1(), face.y1(), face.x2(), face.y2());
            boolean isLive = live >= liveThreshold;

            float[] emb = null;
            if (withEmbedding) emb = recognizer.embed(img, face.landmarks());

            return new FaceAnalysis(true, face.score(), face.landmarks(), emb, pose, live, isLive);
        } finally {
            img.release();
        }
    }
}
