package top.ortus.lightmark.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.ortus.lightmark.common.exception.ApiException;

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
import java.util.ArrayList;
import java.util.List;

@Service
public class ObjectStorageService {

    static final String LATEST_OBJECT_STORAGE_BASE_URL =
            "https://objectstorage.ap-tokyo-1.oraclecloud.com/p/5R94ZnD93i9YTCorrhHEGgkXcgT2tu6J_BD46w3gCc3oJeUa-r-C82LOvxrDvMxE/n/nrrguvtqppqi/b/ortus-bucket/o/";

    private final HttpClient httpClient;
    private final List<String> uploadBaseUrls;

    /**
     * 主构造器：类中存在多个构造器（含包级测试构造器）时，
     * Spring 无法自动选择，必须显式 @Autowired，否则报 "No default constructor found"。
     */
    @Autowired
    public ObjectStorageService(@Value("${lightmark.object-storage.base-url:}") String baseUrl) {
        this(baseUrl, LATEST_OBJECT_STORAGE_BASE_URL);
    }

    ObjectStorageService(String baseUrl, String fallbackBaseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.uploadBaseUrls = buildUploadBaseUrls(baseUrl, fallbackBaseUrl);
    }

    public String uploadAvatar(String normalizedUserId16, MultipartFile file) {
        if (uploadBaseUrls.isEmpty()) {
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

    private String uploadJpeg(String objectName, byte[] jpegBytes) {
        ApiException lastFailure = null;
        for (String uploadBaseUrl : uploadBaseUrls) {
            try {
                return uploadJpeg(uploadBaseUrl, objectName, jpegBytes);
            } catch (ApiException ex) {
                lastFailure = ex;
            }
        }
        throw lastFailure == null ? new ApiException(500, "object storage not configured") : lastFailure;
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

    private List<String> buildUploadBaseUrls(String primaryBaseUrl, String fallbackBaseUrl) {
        List<String> urls = new ArrayList<>();
        addIfPresent(urls, primaryBaseUrl);
        addIfPresent(urls, fallbackBaseUrl);
        return urls;
    }

    private void addIfPresent(List<String> urls, String url) {
        String normalized = url == null ? "" : url.trim();
        if (!normalized.isBlank() && !urls.contains(normalized)) {
            urls.add(normalized);
        }
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
