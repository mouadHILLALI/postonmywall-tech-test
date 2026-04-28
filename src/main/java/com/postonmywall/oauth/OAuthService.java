package com.postonmywall.oauth;

import com.postonmywall.account.SocialAccountService;
import com.postonmywall.common.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OAuthService {

    // ── Twitter / X (OAuth 2.0 + PKCE) ──────────────────────────
    @Value("${oauth.twitter.client-id:}") private String twitterClientId;
    @Value("${oauth.twitter.client-secret:}") private String twitterClientSecret;

    // ── Instagram / Facebook ─────────────────────────────────────
    @Value("${oauth.instagram.client-id:}") private String instagramClientId;
    @Value("${oauth.instagram.client-secret:}") private String instagramClientSecret;

    // ── TikTok ───────────────────────────────────────────────────
    @Value("${oauth.tiktok.client-key:}") private String tiktokClientKey;
    @Value("${oauth.tiktok.client-secret:}") private String tiktokClientSecret;
    /** TikTok requires HTTPS — set to your ngrok URL in dev, same as callback-base-url in prod. */
    @Value("${oauth.tiktok.callback-base-url:}") private String tiktokCallbackBaseUrl;

    // ── YouTube / Google ─────────────────────────────────────────
    @Value("${oauth.youtube.client-id:}") private String youtubeClientId;
    @Value("${oauth.youtube.client-secret:}") private String youtubeClientSecret;

    /** The backend's public base URL — used as redirect_uri registered with each platform. */
    @Value("${oauth.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    /** The frontend's base URL — used to redirect the browser after the OAuth callback. */
    @Value("${oauth.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    private final OAuthStateStore stateStore;
    private final SocialAccountService accountService;
    private final WebClient webClient;

    public OAuthService(OAuthStateStore stateStore,
                        SocialAccountService accountService,
                        WebClient.Builder webClientBuilder) {
        this.stateStore = stateStore;
        this.accountService = accountService;
        this.webClient = webClientBuilder.build();
    }

    // ── Public API ────────────────────────────────────────────────

    public String buildAuthorizationUrl(UUID userId, Platform platform, String frontendRedirectUri) {
        return switch (platform) {
            case TWITTER   -> buildTwitterUrl(userId, frontendRedirectUri);
            case INSTAGRAM -> buildInstagramUrl(userId, frontendRedirectUri);
            case TIKTOK    -> buildTikTokUrl(userId, frontendRedirectUri);
            case YOUTUBE   -> buildYouTubeUrl(userId, frontendRedirectUri);
        };
    }

    public OAuthCallbackResult handleCallback(String code, String state, String error) {
        OAuthStateStore.OAuthState oauthState = stateStore.consume(state).orElse(null);
        if (oauthState == null) {
            // State invalid — redirect to a generic frontend path
            return OAuthCallbackResult.error(null, frontendBaseUrl + "/oauth/callback",
                    "Invalid or expired authorization request. Please try again.");
        }

        if (error != null) {
            return OAuthCallbackResult.error(oauthState.platform(), oauthState.frontendRedirectUri(), error);
        }

        try {
            return switch (oauthState.platform()) {
                case TWITTER   -> handleTwitterCallback(code, oauthState);
                case INSTAGRAM -> handleInstagramCallback(code, oauthState);
                case TIKTOK    -> handleTikTokCallback(code, oauthState);
                case YOUTUBE   -> handleYouTubeCallback(code, oauthState);
            };
        } catch (Exception e) {
            log.error("OAuth callback error for {}: {}", oauthState.platform(), e.getMessage(), e);
            return OAuthCallbackResult.error(oauthState.platform(), oauthState.frontendRedirectUri(),
                    e.getMessage() != null ? e.getMessage() : "Failed to connect account. Please try again.");
        }
    }

    // ── Twitter ───────────────────────────────────────────────────

    private String buildTwitterUrl(UUID userId, String frontendRedirectUri) {
        String verifier  = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);
        String state     = stateStore.save(userId, Platform.TWITTER, frontendRedirectUri, verifier);

        return UriComponentsBuilder.fromHttpUrl("https://twitter.com/i/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", twitterClientId)
                .queryParam("redirect_uri", backendCallback())
                .queryParam("scope", "tweet.read tweet.write users.read offline.access")
                .queryParam("state", state)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }

    private OAuthCallbackResult handleTwitterCallback(String code, OAuthStateStore.OAuthState oauthState) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", backendCallback());
        form.add("code_verifier", oauthState.codeVerifier());

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (twitterClientId + ":" + twitterClientSecret).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> tokenRes = postForm("https://api.twitter.com/2/oauth2/token", form, basicAuth);
        String accessToken  = (String) tokenRes.get("access_token");
        String refreshToken = (String) tokenRes.get("refresh_token");
        Object expiresIn    = tokenRes.get("expires_in");
        Instant expiresAt   = expiresIn != null ? Instant.now().plusSeconds(((Number) expiresIn).longValue()) : null;

        Map<String, Object> userRes = getJson("https://api.twitter.com/2/users/me", accessToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) userRes.get("data");
        String username = "@" + data.get("username");

        accountService.upsertOAuthAccount(oauthState.userId(), Platform.TWITTER,
                username, accessToken, refreshToken, expiresAt);
        return OAuthCallbackResult.success(Platform.TWITTER, oauthState.frontendRedirectUri());
    }

    // ── Instagram Business Login API ──────────────────────────────

    private String buildInstagramUrl(UUID userId, String frontendRedirectUri) {
        String state = stateStore.save(userId, Platform.INSTAGRAM, frontendRedirectUri, null);
        return UriComponentsBuilder.fromHttpUrl("https://www.facebook.com/v19.0/dialog/oauth")
                .queryParam("client_id", instagramClientId)
                .queryParam("redirect_uri", backendCallback())
                .queryParam("scope", "instagram_basic,instagram_content_publish")
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .encode().build().toUriString();
    }

    private OAuthCallbackResult handleInstagramCallback(String code, OAuthStateStore.OAuthState oauthState) {
        // Exchange code for short-lived token
        String tokenUrl = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/v19.0/oauth/access_token")
                .queryParam("client_id", instagramClientId)
                .queryParam("client_secret", instagramClientSecret)
                .queryParam("redirect_uri", backendCallback())
                .queryParam("code", code)
                .encode().build().toUriString();

        Map<String, Object> shortToken = getJson(tokenUrl, null);
        String shortLivedToken = (String) shortToken.get("access_token");

        // Exchange for long-lived token (~60 days)
        String longTokenUrl = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/v19.0/oauth/access_token")
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", instagramClientId)
                .queryParam("client_secret", instagramClientSecret)
                .queryParam("fb_exchange_token", shortLivedToken)
                .encode().build().toUriString();

        Map<String, Object> longToken = getJson(longTokenUrl, null);
        String accessToken = (String) longToken.get("access_token");
        Object expiresIn   = longToken.get("expires_in");
        Instant expiresAt  = expiresIn != null ? Instant.now().plusSeconds(((Number) expiresIn).longValue()) : null;

        // Get Facebook user name as a display identifier
        String meUrl = "https://graph.facebook.com/v19.0/me?fields=id,name&access_token="
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        Map<String, Object> me = getJson(meUrl, null);
        String accountId = (String) me.getOrDefault("name", me.get("id"));

        accountService.upsertOAuthAccount(oauthState.userId(), Platform.INSTAGRAM,
                accountId, accessToken, null, expiresAt);
        return OAuthCallbackResult.success(Platform.INSTAGRAM, oauthState.frontendRedirectUri());
    }

    // ── TikTok ────────────────────────────────────────────────────

    private String buildTikTokUrl(UUID userId, String frontendRedirectUri) {
        String state = stateStore.save(userId, Platform.TIKTOK, frontendRedirectUri, null);
        return UriComponentsBuilder.fromHttpUrl("https://www.tiktok.com/v2/auth/authorize/")
                .queryParam("client_key", tiktokClientKey)
                .queryParam("redirect_uri", tiktokCallback())
                .queryParam("scope", "user.info.basic,video.upload")
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .encode().build().toUriString();
    }

    private OAuthCallbackResult handleTikTokCallback(String code, OAuthStateStore.OAuthState oauthState) {
        String redirectUri = tiktokCallback();
        // Re-encode the code so characters like '*' and '!' that Java's URLEncoder
        // treats as safe are properly percent-encoded for TikTok's token endpoint.
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        log.debug("[TIKTOK] token exchange — client_key='{}' redirect_uri='{}' raw_code='{}' encoded_code='{}'",
                tiktokClientKey, redirectUri, code, encodedCode);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", tiktokClientKey);
        form.add("client_secret", tiktokClientSecret);
        form.add("code", encodedCode);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);

        Map<String, Object> tokenRes = postForm("https://open.tiktokapis.com/v2/oauth/token/", form, null);
        log.debug("TikTok token response: {}", tokenRes);
        String accessToken  = (String) tokenRes.get("access_token");
        String refreshToken = (String) tokenRes.get("refresh_token");
        Object expiresIn    = tokenRes.get("expires_in");
        Instant expiresAt   = expiresIn != null ? Instant.now().plusSeconds(((Number) expiresIn).longValue()) : null;
        if (accessToken == null) {
            throw new RuntimeException("TikTok token exchange failed: " + tokenRes);
        }

        // Get display name
        Map<String, Object> userRes = getJson(
                "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name", accessToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) ((Map<String, Object>) userRes.get("data")).get("user");
        String displayName = (String) userData.getOrDefault("display_name", "tiktok_user");

        accountService.upsertOAuthAccount(oauthState.userId(), Platform.TIKTOK,
                displayName, accessToken, refreshToken, expiresAt);
        return OAuthCallbackResult.success(Platform.TIKTOK, oauthState.frontendRedirectUri());
    }

    // ── YouTube (Google) ──────────────────────────────────────────

    private String buildYouTubeUrl(UUID userId, String frontendRedirectUri) {
        String state = stateStore.save(userId, Platform.YOUTUBE, frontendRedirectUri, null);
        return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", youtubeClientId)
                .queryParam("redirect_uri", backendCallback())
                .queryParam("scope", "https://www.googleapis.com/auth/youtube")
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .encode().build().toUriString();
    }

    private OAuthCallbackResult handleYouTubeCallback(String code, OAuthStateStore.OAuthState oauthState) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", youtubeClientId);
        form.add("client_secret", youtubeClientSecret);
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", backendCallback());

        Map<String, Object> tokenRes = postForm("https://oauth2.googleapis.com/token", form, null);
        String accessToken  = (String) tokenRes.get("access_token");
        String refreshToken = (String) tokenRes.get("refresh_token");
        Object expiresIn    = tokenRes.get("expires_in");
        Instant expiresAt   = expiresIn != null ? Instant.now().plusSeconds(((Number) expiresIn).longValue()) : null;

        Map<String, Object> channelRes = getJson(
                "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true", accessToken);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) channelRes.get("items");
        String channelTitle = "youtube_channel";
        if (items != null && !items.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> snippet = (Map<String, Object>) items.get(0).get("snippet");
            if (snippet != null) channelTitle = (String) snippet.getOrDefault("title", channelTitle);
        }

        accountService.upsertOAuthAccount(oauthState.userId(), Platform.YOUTUBE,
                channelTitle, accessToken, refreshToken, expiresAt);
        return OAuthCallbackResult.success(Platform.YOUTUBE, oauthState.frontendRedirectUri());
    }

    // ── HTTP helpers ──────────────────────────────────────────────

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form, String authHeader) {
        var spec = webClient.post().uri(url);
        if (authHeader != null) spec = spec.header("Authorization", authHeader);
        return spec
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    /** GET with optional Bearer token. Null token means no Authorization header (use query-param tokens). */
    private Map<String, Object> getJson(String url, String bearerToken) {
        var spec = webClient.get().uri(url);
        if (bearerToken != null) spec = spec.header("Authorization", "Bearer " + bearerToken);
        return spec
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    // ── PKCE helpers (Twitter) ────────────────────────────────────

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private String backendCallback() {
        return callbackBaseUrl + "/oauth/callback";
    }

    private String tiktokCallback() {
        String base = (tiktokCallbackBaseUrl != null && !tiktokCallbackBaseUrl.isBlank())
                ? tiktokCallbackBaseUrl : callbackBaseUrl;
        return base + "/oauth/callback";
    }
}
