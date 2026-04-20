package com.postonmywall.exception;

public class SocialApiException extends RuntimeException {
    private final String platform;
    public SocialApiException(String platform, String message, Throwable cause) {
        super("[" + platform + "] " + message, cause);
        this.platform = platform;
    }
    public SocialApiException(String platform, String message) {
        this(platform, message, null);
    }
    public String getPlatform() { return platform; }
}
