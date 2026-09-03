package top.ortus.lightmark.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.ortus.lightmark.backend.exception.ApiException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ObjectStorageService {

    private static final DateTimeFormatter OBJECT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String COMMUNITY_IMAGE_BASE_URL =
            "https://objectstorage.ap-tokyo-1.oraclecloud.com/p/gpXawfcE00rWWXvf7bRiwadiSeQjLY_ceX-7BGAuQpj0ayMJofdnp5mLD2cFzdNV/n/nrrguvtqppqi/b/ortus-bucket/o/";

    private final HttpClient httpClient;
    private final String baseUrl;

    public ObjectStorageService(@Value("${lightmark.object-storage.base-url:}") String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String uploadAvatar(String normalizedUserId16, MultipartFile file) {
        if (baseUrl.isBlank()) {
            throw new ApiException(500, "object storage not configured");
        }
        if (normalizedUserId16 == null || normalizedUserId16.isBlank()) {
            throw new ApiException(400, "invalid user id");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "file is required");
        }
        if (file.getSize() > 2L * 1024L * 1024L) {
            throw new ApiException(400, "file too large");
        }

        byte[] jpegBytes = toJpeg(file);
        String objectName = "avatar-" + normalizedUserId16 + ".jpg";
        return uploadJpeg(objectName, jpegBytes);
    }

    public String uploadPostImage(String normalizedUserId16, MultipartFile file) {
        if (normalizedUserId16 == null || normalizedUserId16.isBlank()) {
            throw new ApiException(400, "invalid user id");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "file is required");
        }
        if (file.getSize() > 5L * 1024L * 1024L) {
            throw new ApiException(400, "file too large");
        }

        byte[] jpegBytes = toJpeg(file);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String objectName = "post-" + normalizedUserId16 + "-" + LocalDateTime.now().format(OBJECT_TIME) + "-" + suffix + ".jpg";
        uploadJpeg(COMMUNITY_IMAGE_BASE_URL, objectName, jpegBytes);
        return objectName;
    }

    private String uploadJpeg(String objectName, byte[] jpegBytes) {
        return uploadJpeg(baseUrl, objectName, jpegBytes);
    }

    private String uploadJpeg(String uploadBaseUrl, String objectName, byte[] jpegBytes) {
        String url = normalizeBaseUrl(uploadBaseUrl) + "/" + objectName;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(jpegBytes))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(502, "object storage upload failed");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(502, "object storage upload failed: " + ex.getMessage());
        }

        return url;
    }

    private String normalizeBaseUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private byte[] toJpeg(MultipartFile file) {
        try {
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                return file.getBytes();
            }
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new ApiException(400, "invalid image");
            }
            BufferedImage rgbImage = toRgbImage(image);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean ok = ImageIO.write(rgbImage, "jpg", baos);
            if (!ok) {
                throw new ApiException(400, "invalid image");
            }
            return baos.toByteArray();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(400, "invalid image");
        }
    }

    private BufferedImage toRgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }
}
