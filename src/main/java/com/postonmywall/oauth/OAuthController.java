package com.postonmywall.oauth;

import com.postonmywall.common.ApiResponse;
import com.postonmywall.common.Platform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "OAuth", description = "Social media OAuth authorization flow")
public class OAuthController {

    private final OAuthService oauthService;

    public OAuthController(OAuthService oauthService) {
        this.oauthService = oauthService;
    }

    /**
     * Step 1 — frontend calls this (with JWT) to get the platform's authorization URL.
     * redirectUri is the frontend page that will receive the final ?status=... redirect.
     */
    @GetMapping("/api/v1/oauth/{platform}/authorize")
    @Operation(summary = "Get OAuth authorization URL for a platform")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAuthorizationUrl(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Platform platform,
            @RequestParam String redirectUri) {

        UUID userId = UUID.fromString(principal.getUsername());
        String authUrl = oauthService.buildAuthorizationUrl(userId, platform, redirectUri);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("authorizationUrl", authUrl)));
    }

    /**
     * Step 2 — the social platform redirects here after the user grants access.
     * This endpoint is public (no JWT). It exchanges the code for tokens,
     * saves the account, then redirects the browser back to the frontend.
     */
    @GetMapping("/oauth/callback")
    @Operation(summary = "OAuth callback — handles platform redirects")
    public void handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpServletResponse response) throws IOException {

        String errorMsg = error != null ? error : errorDescription;
        OAuthCallbackResult result = oauthService.handleCallback(code, state, errorMsg);

        String platform = result.platform() != null ? result.platform().name() : "";
        String redirectUrl;
        if (result.success()) {
            redirectUrl = result.frontendRedirectUri() + "?status=success&platform=" + platform;
        } else {
            String encodedMsg = URLEncoder.encode(
                    result.errorMessage() != null ? result.errorMessage() : "Unknown error",
                    StandardCharsets.UTF_8);
            redirectUrl = result.frontendRedirectUri() + "?status=error&platform=" + platform + "&message=" + encodedMsg;
        }

        response.sendRedirect(redirectUrl);
    }
}
