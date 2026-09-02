package top.ortus.lightmark.user.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
public class QqSmtpEmailService {

    private static final Logger log = LoggerFactory.getLogger(QqSmtpEmailService.class);

    private final LightmarkAuthProperties authProperties;

    public QqSmtpEmailService(LightmarkAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public void sendVerificationCode(String email, String code) {
        String host = authProperties.getMail().getHost();
        int port = authProperties.getMail().getPort();
        String username = authProperties.getMail().getUsername();
        String password = authProperties.getMail().getPassword();
        String fromEmail = authProperties.getMail().getFromEmail();
        if (host == null || host.isBlank()) {
            throw new ApiException(500, "qq smtp host is not configured");
        }
        if (username == null || username.isBlank()) {
            throw new ApiException(500, "qq smtp username is not configured");
        }
        if (password == null || password.isBlank()) {
            throw new ApiException(500, "qq smtp password is not configured");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new ApiException(500, "qq smtp from email is not configured");
        }

        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", String.valueOf(port));
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.ssl.enable", String.valueOf(authProperties.getMail().isSslEnabled()));
            properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
            properties.put("mail.mime.charset", StandardCharsets.UTF_8.name());

            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, authProperties.getMail().getFromName(), StandardCharsets.UTF_8.name()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email, false));
            message.setSubject("拾光旅行验证码", StandardCharsets.UTF_8.name());
            message.setContent(
                    "<p>您的验证码是：<strong>" + escapeHtml(code) + "</strong></p>"
                            + "<p>请在 10 分钟内完成验证。</p>",
                    "text/html; charset=UTF-8"
            );
            message.saveChanges();
            Transport.send(message);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("QQ SMTP send failed, host={}, port={}, sslEnabled={}, username={}, fromEmail={}, to={}, error={}",
                    host,
                    port,
                    authProperties.getMail().isSslEnabled(),
                    username,
                    fromEmail,
                    email,
                    ex.getMessage(),
                    ex);
            throw new ApiException(502, "qq smtp send failed: " + ex.getMessage());
        }
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
