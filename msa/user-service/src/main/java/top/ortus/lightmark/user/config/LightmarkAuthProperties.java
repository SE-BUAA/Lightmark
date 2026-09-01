package top.ortus.lightmark.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "lightmark.auth")
public class LightmarkAuthProperties {

    private final Captcha captcha = new Captcha();
    private final Verification verification = new Verification();
    private final Mail mail = new Mail();

    public Captcha getCaptcha() {
        return captcha;
    }

    public Verification getVerification() {
        return verification;
    }

    public Mail getMail() {
        return mail;
    }

    public static class Captcha {
        private String sessionKey = "auth:captcha";
        private int length = 4;
        private boolean caseSensitive = false;

        public String getSessionKey() {
            return sessionKey;
        }

        public void setSessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        public void setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }
    }

    public static class Verification {
        private int codeLength = 6;
        private int expireMinutes = 10;
        private int resendSeconds = 60;
        private int maxSendPerHour = 5;
        private final List<String> allowedEmailDomains = new ArrayList<>(List.of(
                "gmail.com",
                "outlook.com",
                "hotmail.com",
                "icloud.com",
                "qq.com",
                "163.com",
                "126.com",
                "sina.com",
                "foxmail.com"
        ));
        private boolean allowEduCn = true;

        public int getCodeLength() {
            return codeLength;
        }

        public void setCodeLength(int codeLength) {
            this.codeLength = codeLength;
        }

        public int getExpireMinutes() {
            return expireMinutes;
        }

        public void setExpireMinutes(int expireMinutes) {
            this.expireMinutes = expireMinutes;
        }

        public int getResendSeconds() {
            return resendSeconds;
        }

        public void setResendSeconds(int resendSeconds) {
            this.resendSeconds = resendSeconds;
        }

        public int getMaxSendPerHour() {
            return maxSendPerHour;
        }

        public void setMaxSendPerHour(int maxSendPerHour) {
            this.maxSendPerHour = maxSendPerHour;
        }

        public List<String> getAllowedEmailDomains() {
            return allowedEmailDomains;
        }

        public boolean isAllowEduCn() {
            return allowEduCn;
        }

        public void setAllowEduCn(boolean allowEduCn) {
            this.allowEduCn = allowEduCn;
        }
    }

    public static class Mail {
        private String host = "smtp.qq.com";
        private int port = 465;
        private String username = "";
        private String password = "";
        private String fromEmail = "";
        private String fromName = "lightmark";
        private boolean sslEnabled = true;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFromEmail() {
            return fromEmail;
        }

        public void setFromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public boolean isSslEnabled() {
            return sslEnabled;
        }

        public void setSslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }
    }
}
