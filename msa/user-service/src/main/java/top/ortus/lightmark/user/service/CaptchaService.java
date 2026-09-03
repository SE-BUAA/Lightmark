package top.ortus.lightmark.user.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.common.exception.ApiException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Locale;

import javax.imageio.ImageIO;

@Service
public class CaptchaService {

    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final LightmarkAuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CaptchaService(LightmarkAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public void writeCaptchaImage(OutputStream outputStream, HttpSession session) throws IOException {
        String code = generateCode();
        session.setAttribute(authProperties.getCaptcha().getSessionKey(), code);
        BufferedImage image = renderImage(code);
        ImageIO.write(image, "png", outputStream);
    }

    public void verifyOrThrow(String input, HttpSession session) {
        Object raw = session.getAttribute(authProperties.getCaptcha().getSessionKey());
        if (!(raw instanceof String expected) || expected.isBlank()) {
            throw new ApiException(400, "captcha expired");
        }
        if (input == null || input.isBlank()) {
            throw new ApiException(400, "captcha is required");
        }
        boolean matched = authProperties.getCaptcha().isCaseSensitive()
                ? expected.equals(input.trim())
                : expected.equalsIgnoreCase(input.trim());
        if (!matched) {
            throw new ApiException(400, "captcha invalid");
        }
        session.removeAttribute(authProperties.getCaptcha().getSessionKey());
    }

    private String generateCode() {
        int length = Math.max(4, authProperties.getCaptcha().getLength());
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CAPTCHA_CHARS.charAt(secureRandom.nextInt(CAPTCHA_CHARS.length())));
        }
        return builder.toString();
    }

    private BufferedImage renderImage(String code) {
        int width = 120;
        int height = 42;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(220, 220, 220));
            for (int i = 0; i < 10; i++) {
                int x1 = secureRandom.nextInt(width);
                int y1 = secureRandom.nextInt(height);
                int x2 = secureRandom.nextInt(width);
                int y2 = secureRandom.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }
            g.setFont(new Font("Arial", Font.BOLD, 24));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(20 + secureRandom.nextInt(120), 20 + secureRandom.nextInt(120), 20 + secureRandom.nextInt(120)));
                int x = 16 + i * 22;
                int y = 28 + secureRandom.nextInt(6);
                g.drawString(String.valueOf(code.charAt(i)).toUpperCase(Locale.ROOT), x, y);
            }
        } finally {
            g.dispose();
        }
        return image;
    }
}
