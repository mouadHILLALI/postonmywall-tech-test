package com.postonmywall.oauth;

import com.postonmywall.common.Platform;

public record OAuthCallbackResult(
        boolean success,
        Platform platform,
        String frontendRedirectUri,
        String errorMessage
) {
    static OAuthCallbackResult success(Platform platform, String frontendRedirectUri) {
        return new OAuthCallbackResult(true, platform, frontendRedirectUri, null);
    }

    static OAuthCallbackResult error(Platform platform, String frontendRedirectUri, String message) {
        return new OAuthCallbackResult(false, platform, frontendRedirectUri, message);
    }
}
