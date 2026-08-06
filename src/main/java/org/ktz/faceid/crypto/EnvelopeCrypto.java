package org.ktz.faceid.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Envelope encryption:
 *   - per-record DEK (AES-256) encrypts the template embedding.
 *   - DEK is wrapped by the KMS/HSM master key.
 *
 * The master-key wrapping here is a LOCAL STUB. In production, replace
 * {@link #wrapDek}/{@link #unwrapDek} with real KMS/HSM calls
 * (e.g. AWS KMS Encrypt/Decrypt against kmsKeyId). The rest stays the same.
 */
@Service
public class EnvelopeCrypto {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private final SecureRandom rnd = new SecureRandom();
    private final SecretKey masterKey;
    private final String kmsKeyId;

    public EnvelopeCrypto(@Value("${crypto.master-key-base64}") String masterKeyB64,
                          @Value("${crypto.kms-key-id}") String kmsKeyId) {
        this.masterKey = new SecretKeySpec(Base64.getDecoder().decode(masterKeyB64), "AES");
        this.kmsKeyId = kmsKeyId;
    }

    public String kmsKeyId() { return kmsKeyId; }

    public record Sealed(byte[] ciphertext, byte[] wrappedDek) {}

    /** Encrypt plaintext with a fresh DEK; return ciphertext + wrapped DEK. */
    public Sealed seal(byte[] plaintext) {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey dek = kg.generateKey();
            byte[] ct = aesGcm(Cipher.ENCRYPT_MODE, dek, plaintext, null);
            byte[] wrapped = wrapDek(dek.getEncoded());
            return new Sealed(ct, wrapped);
        } catch (Exception e) {
            throw new RuntimeException("seal failed", e);
        }
    }

    public byte[] open(byte[] ciphertext, byte[] wrappedDek) {
        try {
            byte[] dekRaw = unwrapDek(wrappedDek);
            SecretKey dek = new SecretKeySpec(dekRaw, "AES");
            return aesGcm(Cipher.DECRYPT_MODE, dek, ciphertext, null);
        } catch (Exception e) {
            throw new RuntimeException("open failed", e);
        }
    }

    // ---- KMS/HSM boundary (replace with real calls) ----
    private byte[] wrapDek(byte[] dek) throws Exception {
        return aesGcm(Cipher.ENCRYPT_MODE, masterKey, dek, null);
    }

    private byte[] unwrapDek(byte[] wrapped) throws Exception {
        return aesGcm(Cipher.DECRYPT_MODE, masterKey, wrapped, null);
    }

    // ---- AES-GCM with prepended IV ----
    private byte[] aesGcm(int mode, SecretKey key, byte[] data, byte[] aad) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        if (mode == Cipher.ENCRYPT_MODE) {
            byte[] iv = new byte[IV_LEN];
            rnd.nextBytes(iv);
            c.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null) c.updateAAD(aad);
            byte[] ct = c.doFinal(data);
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return out;
        } else {
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(data, 0, iv, 0, IV_LEN);
            c.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null) c.updateAAD(aad);
            return c.doFinal(data, IV_LEN, data.length - IV_LEN);
        }
    }

    // ---- float[] <-> byte[] helpers for embeddings ----
    public static byte[] floatsToBytes(float[] v) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(v.length * 4);
        for (float f : v) bb.putFloat(f);
        return bb.array();
    }

    public static float[] bytesToFloats(byte[] b) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
        float[] v = new float[b.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = bb.getFloat();
        return v;
    }
    public byte[] sealToBlob(byte[] plaintext) {
        Sealed s = seal(plaintext);
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(
                4 + s.wrappedDek().length + s.ciphertext().length);
        bb.putInt(s.wrappedDek().length);
        bb.put(s.wrappedDek());
        bb.put(s.ciphertext());
        return bb.array();
    }
    public byte[] openBlob(byte[] blob) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(blob);
        int wlen = bb.getInt();
        byte[] wrapped = new byte[wlen];
        bb.get(wrapped);
        byte[] ct = new byte[bb.remaining()];
        bb.get(ct);
        return open(ct, wrapped);
    }
}
