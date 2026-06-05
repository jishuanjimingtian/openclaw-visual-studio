package com.openclaw.vs.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Loads the Ed25519 device identity from OpenClaw's {@code ~/.openclaw/identity/device.json}
 * and signs gateway connect payloads (V3).
 */
public final class OpenClawDeviceIdentity {

    private static final byte[] ED25519_SPKI_PREFIX = hexToBytes("302a300506032b6570032100");

    private final String deviceId;
    private final PrivateKey privateKey;
    private final String publicKeyRawBase64Url;

    private OpenClawDeviceIdentity(String deviceId, PrivateKey privateKey, String publicKeyRawBase64Url) {
        this.deviceId = deviceId;
        this.privateKey = privateKey;
        this.publicKeyRawBase64Url = publicKeyRawBase64Url;
    }

    public static Optional<OpenClawDeviceIdentity> load(Path identityFile) {
        if (!Files.isRegularFile(identityFile)) {
            return Optional.empty();
        }
        try {
            JsonNode root = new ObjectMapper().readTree(identityFile.toFile());
            String publicKeyPem = root.path("publicKeyPem").asText(null);
            String privateKeyPem = root.path("privateKeyPem").asText(null);
            if (publicKeyPem == null || privateKeyPem == null) {
                return Optional.empty();
            }

            byte[] publicKeyDer = decodePem(publicKeyPem);
            byte[] privateKeyDer = decodePem(privateKeyPem);
            byte[] publicKeyRaw = extractEd25519RawPublicKey(publicKeyDer);

            PrivateKey privateKey = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));

            String deviceId = sha256Hex(publicKeyRaw);
            String publicKeyRawBase64Url = base64UrlEncode(publicKeyRaw);
            return Optional.of(new OpenClawDeviceIdentity(deviceId, privateKey, publicKeyRawBase64Url));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPublicKeyRawBase64Url() {
        return publicKeyRawBase64Url;
    }

    public String buildAuthPayloadV3(
        String clientId,
        String clientMode,
        String role,
        String scopesCsv,
        long signedAtMs,
        String token,
        String nonce,
        String platform,
        String deviceFamily
    ) {
        return String.join("|",
            "v3",
            deviceId,
            clientId,
            clientMode,
            role,
            scopesCsv,
            Long.toString(signedAtMs),
            token == null ? "" : token,
            nonce,
            normalizeMetadata(platform),
            normalizeMetadata(deviceFamily)
        );
    }

    public String signPayload(String payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(signature.sign());
    }

    private static String normalizeMetadata(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase();
    }

    private static byte[] extractEd25519RawPublicKey(byte[] spkiDer) throws Exception {
        if (spkiDer.length == ED25519_SPKI_PREFIX.length + 32
            && Arrays.equals(spkiDer, 0, ED25519_SPKI_PREFIX.length, ED25519_SPKI_PREFIX, 0, ED25519_SPKI_PREFIX.length)) {
            return Arrays.copyOfRange(spkiDer, ED25519_SPKI_PREFIX.length, spkiDer.length);
        }
        KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spkiDer));
        return Arrays.copyOfRange(spkiDer, spkiDer.length - 32, spkiDer.length);
    }

    private static byte[] decodePem(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

}
