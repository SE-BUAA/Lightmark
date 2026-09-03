package top.ortus.lightmark.content.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.ortus.lightmark.common.exception.ApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Stores community images and returns only the object name for database persistence. */
@Service
public class CommunityImageStorageService {
    public static final String COMMUNITY_IMAGE_BASE_URL =
            "https://objectstorage.ap-tokyo-1.oraclecloud.com/p/DrCIcuZzY23irWZEg-Z28KiNqiaYAhxmr9dHddU1uS-GuopaYi6TCQ7Ok7lRCU0C/n/nrrguvtqppqi/b/ortus-bucket/o/";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String upload(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "file is required");
        if (file.getSize() > 5L * 1024L * 1024L) throw new ApiException(400, "file too large");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) throw new ApiException(400, "invalid image");
        String objectName = "post-" + userId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension(contentType);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(COMMUNITY_IMAGE_BASE_URL + objectName))
                    .timeout(Duration.ofSeconds(45)).header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes())).build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) throw new ApiException(502, "object storage upload failed");
            return objectName;
        } catch (ApiException ex) { throw ex;
        } catch (Exception ex) { throw new ApiException(502, "object storage upload failed"); }
    }

    private String extension(String contentType) {
        if (contentType.contains("png")) return "png";
        if (contentType.contains("webp")) return "webp";
        return "jpg";
    }
}
