package org.ktz.faceid.onnx;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.Map;

/**
 * MiniFASNet-V2 face anti-spoofing (Silent-Face-Anti-Spoofing).
 * Input: (1,3,80,80) float32 BGR, raw pixel values [0,255] (NO /255 normalization).
 * Output: 3-class softmax; liveness class = index 1.
 * Liveness score = p[1].
 *
 * Crop rule: 2.7x margin box around the face bbox center, resized to 80x80, NO alignment.
 */
public class AntiSpoofDetector {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final int size = 80;
    private final double scale = 2.7;

    public AntiSpoofDetector(OrtEnvironment env, byte[] modelBytes, int intraThreads) throws OrtException {
        this.env = env;
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(intraThreads);
        this.session = env.createSession(modelBytes, opts);
    }

    /** liveness score in [0,1] from the face bbox (x1,y1,x2,y2) within the full image. */
    public double liveScore(Mat bgr, float x1, float y1, float x2, float y2) throws OrtException {
        Mat crop = cropWithMargin(bgr, x1, y1, x2, y2);
        Mat resized = new Mat();
        Imgproc.resize(crop, resized, new Size(size, size));
        float[] chw = toChw(resized);
        crop.release();
        resized.release();

        long[] shape = {1, 3, size, size};
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result out = session.run(Map.of(session.getInputNames().iterator().next(), input))) {
            float[][] raw = (float[][]) out.get(0).getValue();
            float[] logits = raw[0];
            float[] probs = softmax(logits);
            System.out.println("ANTISPOOF probs=" + java.util.Arrays.toString(probs));
            return probs[1];
        }
    }

    /** 2.7x margin box around the bbox center, clamped to image bounds. */
    private Mat cropWithMargin(Mat img, float x1, float y1, float x2, float y2) {
        double cx = (x1 + x2) / 2.0;
        double cy = (y1 + y2) / 2.0;
        double w = (x2 - x1) * scale;
        double h = (y2 - y1) * scale;
        int nx1 = (int) Math.max(0, cx - w / 2);
        int ny1 = (int) Math.max(0, cy - h / 2);
        int nx2 = (int) Math.min(img.width(), cx + w / 2);
        int ny2 = (int) Math.min(img.height(), cy + h / 2);
        return new Mat(img, new Rect(nx1, ny1, Math.max(1, nx2 - nx1), Math.max(1, ny2 - ny1)));
    }

    /** BGR HWC uint8 -> BGR CHW float, pixel/255 (NO channel swap, model expects BGR). */
    private float[] toChw(Mat bgr) {
        int h = bgr.height(), w = bgr.width();
        byte[] px = new byte[h * w * 3];
        bgr.get(0, 0, px);
        float[] chw = new float[3 * h * w];
        int plane = h * w;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = (y * w + x) * 3;
                float b = (px[p] & 0xFF);
                float g = (px[p + 1] & 0xFF);
                float r = (px[p + 2] & 0xFF);
                int pos = y * w + x;
                // keep BGR channel order in CHW
                chw[pos]             = b;
                chw[plane + pos]     = g;
                chw[2 * plane + pos] = r;
            }
        }
        return chw;
    }

    private float[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) max = Math.max(max, v);
        double sum = 0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) { exp[i] = Math.exp(logits[i] - max); sum += exp[i]; }
        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) probs[i] = (float) (exp[i] / sum);
        return probs;
    }
}