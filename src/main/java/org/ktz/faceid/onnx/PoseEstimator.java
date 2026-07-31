package org.ktz.faceid.onnx;

import org.ktz.faceid.config.PoseProperties;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Head-pose estimation from SCRFD 5 landmarks, without camera calibration.
 *
 * Geometric heuristic:
 *   - roll  = angle of the eye line vs horizontal.
 *   - yaw   = horizontal asymmetry of the nose relative to the eye-mouth box.
 *   - pitch = vertical position of the nose between eyes and mouth.
 *
 * Angles are approximate but stable enough for FRONT vs LEFT/RIGHT gating.
 *
 * Landmark order (SCRFD): 0=right eye, 1=left eye, 2=nose, 3=right mouth, 4=left mouth.
 * Coordinates are in image space (x grows right, y grows down).
 */
@Component
@EnableConfigurationProperties(PoseProperties.class)
public class PoseEstimator {

    private final PoseProperties props;

    public PoseEstimator(PoseProperties props) {
        this.props = props;
    }

    /** Compute pose + quality for a face, given its landmarks and the source image. */
    public PoseResult estimate(float[] lmk, Mat bgrFace) {
        double rEyeX = lmk[0], rEyeY = lmk[1];
        double lEyeX = lmk[2], lEyeY = lmk[3];
        double noseX = lmk[4], noseY = lmk[5];
        double rMouthX = lmk[6], rMouthY = lmk[7];
        double lMouthX = lmk[8], lMouthY = lmk[9];

        // eye midpoint and mouth midpoint
        double eyeMidX = (rEyeX + lEyeX) / 2.0;
        double eyeMidY = (rEyeY + lEyeY) / 2.0;
        double mouthMidX = (rMouthX + lMouthX) / 2.0;
        double mouthMidY = (rMouthY + lMouthY) / 2.0;

        // ---- ROLL: tilt of the eye line ----
        double roll = Math.toDegrees(Math.atan2(lEyeY - rEyeY, lEyeX - rEyeX));
        // normalize around 0 (eyes roughly horizontal)
        if (roll > 90) roll -= 180;
        if (roll < -90) roll += 180;

        // interocular distance as scale reference
        double eyeDist = Math.hypot(lEyeX - rEyeX, lEyeY - rEyeY);
        if (eyeDist < 1e-3) {
            return PoseResult.rejected("NO_FACE", "Degenerate landmarks", 0, 0, 0, 0, 0);
        }

        // ---- YAW: nose horizontal offset from eye midpoint, scaled by eye distance ----
        // When head turns right (image), nose shifts left relative to the eye box, etc.
        double noseOffsetX = (noseX - eyeMidX) / eyeDist;
        // empirical gain to map normalized offset -> degrees
        double yaw = noseOffsetX * 90.0;

        // ---- PITCH: nose vertical position between eyes and mouth ----
        double faceH = mouthMidY - eyeMidY;
        double pitch = 0;
        if (Math.abs(faceH) > 1e-3) {
            // ratio 0.5 ~ neutral; deviations => looking up/down
            double noseRatio = (noseY - eyeMidY) / faceH;
            pitch = (noseRatio - 0.5) * 90.0;
        }

        // ---- quality: brightness + blur ----
        double brightness = meanBrightness(bgrFace);
        double blur = blurVariance(bgrFace);

        // quality gates
        if (brightness < props.getMinBrightness() || brightness > props.getMaxBrightness()) {
            return PoseResult.rejected("BAD_LIGHTING",
                    "Brightness out of range", yaw, pitch, roll, brightness, blur);
        }
        if (blur < props.getMinBlurVariance()) {
            return PoseResult.rejected("TOO_BLURRY",
                    "Image too blurry", yaw, pitch, roll, brightness, blur);
        }
        if (Math.abs(roll) > props.getRollMax()) {
            return PoseResult.rejected("POSE_NOT_CONFIRMED",
                    "Head roll too large", yaw, pitch, roll, brightness, blur);
        }
        if (Math.abs(pitch) > props.getPitchMax()) {
            return PoseResult.rejected("POSE_NOT_CONFIRMED",
                    "Head pitch too large", yaw, pitch, roll, brightness, blur);
        }

        return PoseResult.ok(yaw, pitch, roll, brightness, blur);
    }

    /** Check the estimated pose satisfies the requested action's yaw range. */
    public PoseResult checkAction(String action, PoseResult pose) {
        if (!pose.accepted()) return pose; // already rejected on quality/roll/pitch

        double yaw = pose.yaw();
        boolean ok = switch (action) {
            case "HOLD_FRONT" -> Math.abs(yaw) <= props.getFrontYawMax();
            // NOTE: sign convention — nose shifting left in image => negative yaw => TURN_LEFT
            case "TURN_LEFT"  -> yaw <= -props.getTurnYawMin() && yaw >= -props.getTurnYawMax();
            case "TURN_RIGHT" -> yaw >= props.getTurnYawMin() && yaw <= props.getTurnYawMax();
            default -> false;
        };
        if (!ok) {
            return PoseResult.rejected("POSE_NOT_CONFIRMED",
                    "Pose does not match requested action " + action,
                    pose.yaw(), pose.pitch(), pose.roll(),
                    pose.brightness(), pose.blurVariance());
        }
        return pose;
    }

    // ---- helpers ----

    private double meanBrightness(Mat bgr) {
        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
        double mean = Core.mean(gray).val[0];
        gray.release();
        return mean;
    }

    /** Variance of Laplacian — standard blur metric (low variance = blurry). */
    private double blurVariance(Mat bgr) {
        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
        Mat lap = new Mat();
        Imgproc.Laplacian(gray, lap, org.opencv.core.CvType.CV_64F);
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(lap, mu, sigma);
        double std = sigma.get(0, 0)[0];
        gray.release();
        lap.release();
        return std * std; // variance
    }
}