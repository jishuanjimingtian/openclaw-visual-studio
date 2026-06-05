package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.AttachmentCompleteRequest;
import com.openclaw.vs.dto.AttachmentInitRequest;
import com.openclaw.vs.dto.AttachmentInitResponse;
import com.openclaw.vs.dto.ChatAttachmentRefDto;
import com.openclaw.vs.exception.BadRequestException;
import com.openclaw.vs.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentService {

    public static final int CHUNK_SIZE = 4 * 1024 * 1024;
    private static final int THUMB_MAX_PX = 320;
    private static final long STAGING_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private final OpenClawWorkspaceConfigService workspaceConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();

    private record UploadSession(
        String uploadId,
        String name,
        String mime,
        long sizeBytes,
        String expectedSha256,
        Path stagingDir,
        int totalChunks,
        long createdAtMs
    ) {}

    private record AttachmentMeta(
        String attachmentId,
        String name,
        String mime,
        long sizeBytes,
        String sha256,
        Integer width,
        Integer height,
        boolean thumbReady,
        String createdAt
    ) {}

    public AttachmentInitResponse initUpload(AttachmentInitRequest request) throws Exception {
        ChatAttachmentMimeGuard.validateInit(request.getName(), request.getMime(), request.getSizeBytes());
        purgeExpiredStaging();

        String uploadId = UUID.randomUUID().toString();
        Path stagingDir = resolveStagingRoot().resolve(uploadId);
        Files.createDirectories(stagingDir);

        int totalChunks = (int) Math.ceil((double) request.getSizeBytes() / CHUNK_SIZE);
        int maxChunks = (int) Math.ceil((double) ChatAttachmentMimeGuard.maxFileBytes() / CHUNK_SIZE);

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("name", request.getName().trim());
        meta.put("mime", request.getMime().trim());
        meta.put("sizeBytes", request.getSizeBytes());
        if (request.getSha256() != null && !request.getSha256().isBlank()) {
            meta.put("expectedSha256", request.getSha256().trim().toLowerCase(Locale.ROOT));
        }
        meta.put("totalChunks", totalChunks);
        meta.put("createdAtMs", System.currentTimeMillis());
        Files.writeString(stagingDir.resolve("upload.json"), objectMapper.writeValueAsString(meta));

        uploadSessions.put(uploadId, new UploadSession(
            uploadId,
            request.getName().trim(),
            request.getMime().trim(),
            request.getSizeBytes(),
            request.getSha256() != null ? request.getSha256().trim().toLowerCase(Locale.ROOT) : null,
            stagingDir,
            totalChunks,
            System.currentTimeMillis()
        ));

        return AttachmentInitResponse.builder()
            .uploadId(uploadId)
            .chunkSize(CHUNK_SIZE)
            .maxChunks(maxChunks)
            .uploadedChunks(listUploadedChunks(stagingDir, totalChunks))
            .build();
    }

    public void writeChunk(String uploadId, int chunkIndex, InputStream body, long contentLength) throws Exception {
        UploadSession session = requireUploadSession(uploadId);
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) {
            throw new BadRequestException("无效的分片索引: " + chunkIndex);
        }
        if (contentLength <= 0 || contentLength > CHUNK_SIZE) {
            throw new BadRequestException("分片大小无效");
        }

        Path chunkPath = session.stagingDir().resolve("chunk-" + chunkIndex + ".part");
        try (InputStream in = body) {
            Files.copy(in, chunkPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public ChatAttachmentRefDto completeUpload(String uploadId, AttachmentCompleteRequest request) throws Exception {
        UploadSession session = requireUploadSession(uploadId);
        Path stagingDir = session.stagingDir();

        for (int i = 0; i < session.totalChunks(); i++) {
            if (!Files.isRegularFile(stagingDir.resolve("chunk-" + i + ".part"))) {
                throw new BadRequestException("缺少分片: " + i);
            }
        }

        String attachmentId = UUID.randomUUID().toString();
        Path destDir = resolveAttachmentDir(attachmentId);
        Files.createDirectories(destDir);

        String ext = extensionFor(session.name(), session.mime());
        Path original = destDir.resolve("original" + ext);

        try (OutputStream out = Files.newOutputStream(original, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < session.totalChunks(); i++) {
                Path chunk = stagingDir.resolve("chunk-" + i + ".part");
                try (InputStream in = Files.newInputStream(chunk)) {
                    in.transferTo(out);
                }
            }
        }

        long actualSize = Files.size(original);
        if (actualSize != session.sizeBytes()) {
            deleteQuietly(destDir);
            throw new BadRequestException("文件大小校验失败");
        }

        String sha256 = sha256Hex(original);
        if (session.expectedSha256() != null && !session.expectedSha256().equalsIgnoreCase(sha256)) {
            deleteQuietly(destDir);
            throw new BadRequestException("SHA256 校验失败");
        }
        if (request != null && request.getSha256() != null && !request.getSha256().isBlank()
            && !request.getSha256().trim().equalsIgnoreCase(sha256)) {
            deleteQuietly(destDir);
            throw new BadRequestException("SHA256 校验失败");
        }

        Integer width = request != null ? request.getWidth() : null;
        Integer height = request != null ? request.getHeight() : null;
        boolean thumbReady = false;
        if (ChatAttachmentMimeGuard.isImageMime(session.mime())) {
            try {
                thumbReady = generateThumbnail(original, destDir.resolve("thumb.jpg"), width, height);
            } catch (Exception e) {
                log.debug("Thumbnail generation failed for {}: {}", attachmentId, e.getMessage());
            }
        }

        AttachmentMeta meta = new AttachmentMeta(
            attachmentId,
            session.name(),
            session.mime(),
            actualSize,
            sha256,
            width,
            height,
            thumbReady,
            java.time.Instant.now().toString()
        );
        writeMeta(destDir, meta);

        deleteQuietly(stagingDir);
        uploadSessions.remove(uploadId);

        return toRef(meta);
    }

    public ChatAttachmentRefDto getRef(String attachmentId) throws Exception {
        AttachmentMeta meta = readMeta(requireAttachmentDir(attachmentId));
        return toRef(meta);
    }

    public Path resolveAttachmentPath(String attachmentId) throws Exception {
        Path dir = requireAttachmentDir(attachmentId);
        AttachmentMeta meta = readMeta(dir);
        String ext = extensionFor(meta.name(), meta.mime());
        Path original = dir.resolve("original" + ext);
        if (!Files.isRegularFile(original)) {
            throw new NotFoundException("附件文件不存在");
        }
        return original;
    }

    public Path resolveThumbPath(String attachmentId) throws Exception {
        Path dir = requireAttachmentDir(attachmentId);
        Path thumb = dir.resolve("thumb.jpg");
        if (!Files.isRegularFile(thumb)) {
            throw new NotFoundException("缩略图不存在");
        }
        return thumb;
    }

    public boolean hasThumb(String attachmentId) {
        try {
            return Files.isRegularFile(requireAttachmentDir(attachmentId).resolve("thumb.jpg"));
        } catch (Exception e) {
            return false;
        }
    }

    public Path resolveMediaRoot() throws Exception {
        Path workspace = workspaceConfigService.ensureWorkspaceConfigured(false).workspacePath();
        return workspace.resolve(".openclaw-media");
    }

    private Path resolveStagingRoot() throws Exception {
        return resolveMediaRoot().resolve("chat-staging");
    }

    private Path resolveAttachmentDir(String attachmentId) throws Exception {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return resolveMediaRoot().resolve("chat").resolve(month).resolve(attachmentId);
    }

    private Path requireAttachmentDir(String attachmentId) throws Exception {
        if (attachmentId == null || attachmentId.isBlank()) {
            throw new BadRequestException("attachmentId 不能为空");
        }
        String id = attachmentId.trim();
        if (!id.matches("^[0-9a-fA-F-]{36}$")) {
            throw new BadRequestException("无效的 attachmentId");
        }
        Path monthDir = resolveMediaRoot().resolve("chat")
            .resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        Path direct = monthDir.resolve(id);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path chatRoot = resolveMediaRoot().resolve("chat");
        if (Files.isDirectory(chatRoot)) {
            try (Stream<Path> months = Files.list(chatRoot)) {
                var hit = months
                    .filter(Files::isDirectory)
                    .map(p -> p.resolve(id))
                    .filter(Files::isDirectory)
                    .findFirst();
                if (hit.isPresent()) {
                    return hit.get();
                }
            }
        }
        throw new NotFoundException("附件不存在");
    }

    private UploadSession requireUploadSession(String uploadId) throws Exception {
        UploadSession session = uploadSessions.get(uploadId);
        if (session != null) {
            return session;
        }
        Path stagingDir = resolveStagingRoot().resolve(uploadId);
        if (!Files.isDirectory(stagingDir)) {
            throw new NotFoundException("上传会话不存在");
        }
        ObjectNode meta = (ObjectNode) objectMapper.readTree(
            Files.readString(stagingDir.resolve("upload.json"), StandardCharsets.UTF_8));
        return new UploadSession(
            uploadId,
            meta.path("name").asText(),
            meta.path("mime").asText(),
            meta.path("sizeBytes").asLong(),
            meta.path("expectedSha256").asText(null),
            stagingDir,
            meta.path("totalChunks").asInt(),
            meta.path("createdAtMs").asLong(System.currentTimeMillis())
        );
    }

    private static int[] listUploadedChunks(Path stagingDir, int totalChunks) throws Exception {
        List<Integer> uploaded = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (Files.isRegularFile(stagingDir.resolve("chunk-" + i + ".part"))) {
                uploaded.add(i);
            }
        }
        return uploaded.stream().mapToInt(Integer::intValue).toArray();
    }

    private void purgeExpiredStaging() {
        try {
            Path root = resolveStagingRoot();
            if (!Files.isDirectory(root)) {
                return;
            }
            long cutoff = System.currentTimeMillis() - STAGING_MAX_AGE_MS;
            try (Stream<Path> dirs = Files.list(root)) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    try {
                        Path metaFile = dir.resolve("upload.json");
                        long created = Files.exists(metaFile)
                            ? objectMapper.readTree(Files.readString(metaFile, StandardCharsets.UTF_8))
                                .path("createdAtMs").asLong(0L)
                            : Files.getLastModifiedTime(dir).toMillis();
                        if (created > 0 && created < cutoff) {
                            deleteQuietly(dir);
                        }
                    } catch (Exception ignored) {
                        // skip
                    }
                });
            }
        } catch (Exception e) {
            log.debug("Staging purge skipped: {}", e.getMessage());
        }
    }

    private static boolean generateThumbnail(Path original, Path thumbOut, Integer width, Integer height) throws Exception {
        BufferedImage src = ImageIO.read(original.toFile());
        if (src == null) {
            return false;
        }
        int srcW = width != null && width > 0 ? width : src.getWidth();
        int srcH = height != null && height > 0 ? height : src.getHeight();
        double scale = Math.min(1.0, Math.min((double) THUMB_MAX_PX / srcW, (double) THUMB_MAX_PX / srcH));
        int targetW = Math.max(1, (int) Math.round(srcW * scale));
        int targetH = Math.max(1, (int) Math.round(srcH * scale));
        Image scaled = src.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        ImageIO.write(out, "jpg", thumbOut.toFile());
        return true;
    }

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (read > 0) {
                    digest.update(buf, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String extensionFor(String name, String mime) {
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ext.length() <= 8) {
                return ext;
            }
        }
        if (mime != null) {
            return switch (mime.toLowerCase(Locale.ROOT)) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                case "application/pdf" -> ".pdf";
                case "text/plain" -> ".txt";
                case "text/markdown", "text/x-markdown" -> ".md";
                case "text/csv", "application/csv" -> ".csv";
                default -> "";
            };
        }
        return "";
    }

    private void writeMeta(Path dir, AttachmentMeta meta) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("attachmentId", meta.attachmentId());
        node.put("name", meta.name());
        node.put("mime", meta.mime());
        node.put("sizeBytes", meta.sizeBytes());
        node.put("sha256", meta.sha256());
        if (meta.width() != null) {
            node.put("width", meta.width());
        }
        if (meta.height() != null) {
            node.put("height", meta.height());
        }
        node.put("thumbReady", meta.thumbReady());
        node.put("createdAt", meta.createdAt());
        Files.writeString(dir.resolve("meta.json"), objectMapper.writeValueAsString(node), StandardCharsets.UTF_8);
    }

    private AttachmentMeta readMeta(Path dir) throws Exception {
        Path metaFile = dir.resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            throw new NotFoundException("附件元数据不存在");
        }
        var node = objectMapper.readTree(Files.readString(metaFile, StandardCharsets.UTF_8));
        return new AttachmentMeta(
            node.path("attachmentId").asText(),
            node.path("name").asText(),
            node.path("mime").asText(),
            node.path("sizeBytes").asLong(),
            node.path("sha256").asText(null),
            node.has("width") ? node.path("width").asInt() : null,
            node.has("height") ? node.path("height").asInt() : null,
            node.path("thumbReady").asBoolean(false),
            node.path("createdAt").asText(null)
        );
    }

    private static ChatAttachmentRefDto toRef(AttachmentMeta meta) {
        return ChatAttachmentRefDto.builder()
            .attachmentId(meta.attachmentId())
            .name(meta.name())
            .mime(meta.mime())
            .sizeBytes(meta.sizeBytes())
            .width(meta.width())
            .height(meta.height())
            .thumbReady(meta.thumbReady())
            .build();
    }

    private static void deleteQuietly(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // ignore
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
            // ignore
        }
    }
}
