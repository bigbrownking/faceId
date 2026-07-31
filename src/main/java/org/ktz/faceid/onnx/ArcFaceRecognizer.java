package org.ktz.faceid.onnx;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.Map;

/**
 * ArcFace recognizer (buffalo_l w600k_r50.onnx).
 * Input: 112x112 aligned face, RGB CHW, normalized ((x-127.5)/127.5).
 * Output: 512-d embedding (we L2-normalize it).
 */
public class ArcFaceRecognizer {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final int faceSize = 112;

    // Canonical 5-point template for 112x112 alignment (ArcFace standard).
    private static final double[][] REF_PTS = {
            {38.2946, 51.6963},
            {73.5318, 51.5014},
            {56.0252, 71.7366},
            {41.5493, 92.3655},
            {70.7299, 92.2041}
    };

    public ArcFaceRecognizer(OrtEnvironment env, byte[] modelBytes, int intraThreads) throws OrtException {
        this.env = env;
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(intraThreads);
        this.session = env.createSession(modelBytes, opts);
    }

    /** Align by 5 landmarks, run recognizer, return L2-normalized 512-d embedding. */
    public float[] embed(Mat bgr, float[] landmarks) throws OrtException {
        Mat aligned = align(bgr, landmarks);
        float[] chw = toChw(aligned);
        aligned.release();

        long[] shape = {1, 3, faceSize, faceSize};
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result out = session.run(Map.of(session.getInputNames().iterator().next(), input))) {
            float[][] raw = (float[][]) out.get(0).getValue();
            return l2normalize(raw[0]);
        }
    }

    private Mat align(Mat bgr, float[] lmk) {
        Mat src = new Mat(5, 2, CvType.CV_32F);
        Mat dst = new Mat(5, 2, CvType.CV_32F);
        for (int i = 0; i < 5; i++) {
            src.put(i, 0, lmk[i * 2], lmk[i * 2 + 1]);
            dst.put(i, 0, REF_PTS[i][0], REF_PTS[i][1]);
        }
        // Partial affine (similarity) transform from detected -> canonical points.
        Mat m = org.opencv.calib3d.Calib3d.estimateAffinePartial2D(src, dst);
        Mat aligned = new Mat();
        Imgproc.warpAffine(bgr, aligned, m, new Size(faceSize, faceSize));
        src.release(); dst.release(); m.release();
        return aligned;
    }

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
                chw[pos]             = (r - 127.5f) / 127.5f;
                chw[plane + pos]     = (g - 127.5f) / 127.5f;
                chw[2 * plane + pos] = (b - 127.5f) / 127.5f;
            }
        }
        return chw;
    }

    private float[] l2normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum) + 1e-10;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }
}
