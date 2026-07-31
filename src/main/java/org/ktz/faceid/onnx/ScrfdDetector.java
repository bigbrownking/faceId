package org.ktz.faceid.onnx;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SCRFD detector (buffalo_l det_10g.onnx).
 * Outputs face boxes + 5 landmarks. We keep the highest-scoring face,
 * since a single front capture per attempt is expected.
 */
public class ScrfdDetector {

    public record Face(float score, float x1, float y1, float x2, float y2, float[] landmarks) {}

    private final OrtEnvironment env;
    private final OrtSession session;
    private final int inputSize = 640;         // SCRFD 640x640
    private final float scoreThreshold = 0.5f;
    private final float nmsThreshold = 0.4f;
    private final int[] featStrides = {8, 16, 32};
    private final int numAnchors = 2;

    public ScrfdDetector(OrtEnvironment env, byte[] modelBytes, int intraThreads) throws OrtException {
        this.env = env;
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(intraThreads);
        this.session = env.createSession(modelBytes, opts);
    }

    /** Returns best detected face in original image coordinates, or null. */
    public Face detectBest(Mat bgr) throws OrtException {
        float scale = Math.min(inputSize / (float) bgr.width(), inputSize / (float) bgr.height());
        int newW = Math.round(bgr.width() * scale);
        int newH = Math.round(bgr.height() * scale);

        Mat resized = new Mat();
        Imgproc.resize(bgr, resized, new Size(newW, newH));
        Mat canvas = Mat.zeros(inputSize, inputSize, bgr.type());
        resized.copyTo(canvas.submat(0, newH, 0, newW));

        float[] chw = toNormalizedChw(canvas);
        long[] shape = {1, 3, inputSize, inputSize};
        List<Face> faces = new ArrayList<>();

        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result out = session.run(java.util.Map.of(session.getInputNames().iterator().next(), input))) {

            // SCRFD output ordering: [score_8, score_16, score_32, bbox_8, bbox_16, bbox_32, kps_8, kps_16, kps_32]
            for (int i = 0; i < featStrides.length; i++) {
                int stride = featStrides[i];
                float[][] scores = (float[][]) out.get(i).getValue();
                float[][] bboxes = (float[][]) out.get(i + 3).getValue();
                float[][] kpss   = (float[][]) out.get(i + 6).getValue();

                int featW = inputSize / stride;
                int featH = inputSize / stride;
                int idx = 0;
                for (int y = 0; y < featH; y++) {
                    for (int x = 0; x < featW; x++) {
                        for (int a = 0; a < numAnchors; a++) {
                            float s = scores[idx][0];
                            if (s >= scoreThreshold) {
                                float cx = x * stride;
                                float cy = y * stride;
                                float l = bboxes[idx][0] * stride;
                                float t = bboxes[idx][1] * stride;
                                float r = bboxes[idx][2] * stride;
                                float b = bboxes[idx][3] * stride;
                                float x1 = (cx - l) / scale;
                                float y1 = (cy - t) / scale;
                                float x2 = (cx + r) / scale;
                                float y2 = (cy + b) / scale;

                                float[] lmk = new float[10];
                                for (int k = 0; k < 5; k++) {
                                    lmk[k * 2]     = (cx + kpss[idx][k * 2] * stride) / scale;
                                    lmk[k * 2 + 1] = (cy + kpss[idx][k * 2 + 1] * stride) / scale;
                                }
                                faces.add(new Face(s, x1, y1, x2, y2, lmk));
                            }
                            idx++;
                        }
                    }
                }
            }
        }
        resized.release();
        canvas.release();

        List<Face> kept = nms(faces);
        return kept.stream().max(Comparator.comparingDouble(Face::score)).orElse(null);
    }

    private List<Face> nms(List<Face> faces) {
        faces.sort(Comparator.comparingDouble(Face::score).reversed());
        List<Face> keep = new ArrayList<>();
        boolean[] removed = new boolean[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            if (removed[i]) continue;
            Face fi = faces.get(i);
            keep.add(fi);
            for (int j = i + 1; j < faces.size(); j++) {
                if (removed[j]) continue;
                if (iou(fi, faces.get(j)) > nmsThreshold) removed[j] = true;
            }
        }
        return keep;
    }

    private float iou(Face a, Face b) {
        float xx1 = Math.max(a.x1(), b.x1());
        float yy1 = Math.max(a.y1(), b.y1());
        float xx2 = Math.min(a.x2(), b.x2());
        float yy2 = Math.min(a.y2(), b.y2());
        float w = Math.max(0, xx2 - xx1);
        float h = Math.max(0, yy2 - yy1);
        float inter = w * h;
        float areaA = (a.x2() - a.x1()) * (a.y2() - a.y1());
        float areaB = (b.x2() - b.x1()) * (b.y2() - b.y1());
        return inter / (areaA + areaB - inter);
    }

    /** BGR HWC uint8 -> RGB CHW normalized ((x-127.5)/128). */
    private float[] toNormalizedChw(Mat bgr) {
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
                chw[pos]             = (r - 127.5f) / 128f;
                chw[plane + pos]     = (g - 127.5f) / 128f;
                chw[2 * plane + pos] = (b - 127.5f) / 128f;
            }
        }
        return chw;
    }
}
