package top.ortus.lightmark.user.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStorageServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToLatestBaseUrlWhenConfiguredPrefixFails() throws Exception {
        AtomicInteger oldPrefixHits = new AtomicInteger();
        AtomicInteger latestPrefixHits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/expired/avatar-0000000000000042.jpg", exchange -> {
            oldPrefixHits.incrementAndGet();
            respond(exchange, 403);
        });
        server.createContext("/latest/avatar-0000000000000042.jpg", exchange -> {
            latestPrefixHits.incrementAndGet();
            respond(exchange, 200);
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ObjectStorageService service = new ObjectStorageService(baseUrl + "/expired", baseUrl + "/latest");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String avatarUrl = service.uploadAvatar("0000000000000042", file);

        assertEquals(baseUrl + "/latest/avatar-0000000000000042.jpg", avatarUrl);
        assertEquals(1, oldPrefixHits.get());
        assertEquals(1, latestPrefixHits.get());
        assertTrue(avatarUrl.contains("/latest/"));
    }

    private void respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        try (OutputStream ignored = exchange.getResponseBody()) {
            // no response body
        }
    }
}
