package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import com.postonmywall.exception.SocialApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

// ─── Shared base ──────────────────────────────────────────

abstract class BaseAdapter {
    protected static final Duration TIMEOUT = Duration.ofSeconds(15);

    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractData(Map<?, ?> response, String platform) {
        if (response == null) throw new SocialApiException(platform, "Empty response from API");
        Object data = response.get("data");
        if (!(data instanceof Map)) throw new SocialApiException(platform, "Missing 'data' field in response");
        return (Map<String, Object>) data;
    }
}

// ─── TikTok ───────────────────────────────────────────────

@Component
class TikTokAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final Logger log = LoggerFactory.getLogger(TikTokAdapter.class);
    private final WebClient client;

    TikTokAdapter(WebClient.Builder builder,
                  @Value("${social.tiktok.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Platform getPlatform() { return Platform.TIKTOK; }

    @Override
    public String publish(String accountId, String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[TIKTOK] Publishing post: title={}", title);
        try {
            Map<String, Object> body = Map.of(
                    "post_info", Map.of(
                            "title", title.substring(0, Math.min(title.length(), 150)),
                            "privacy_level", "SELF_ONLY",
                            "disable_duet", false,
                            "disable_comment", false,
                            "disable_stitch", false
                    ),
                    "source_info", Map.of(
                            "source", "PULL_FROM_URL",
                            "video_url", mediaUrl
                    )
            );

            @SuppressWarnings("unchecked")
            Map<?, ?> response = client.post()
                    .uri("/post/publish/video/init/")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            Map<String, Object> data = extractData(response, "TIKTOK");
            String postId = String.valueOf(data.get("publish_id"));
            log.info("[TIKTOK] Published successfully: postId={}", postId);
            return postId;

        } catch (WebClientResponseException ex) {
            throw new SocialApiException("TIKTOK", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public void remove(String accessToken, String externalPostId) {
        log.info("[TIKTOK] Removing post: postId={}", externalPostId);
        try {
            client.post()
                    .uri("/post/delete/")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("publish_id", externalPostId))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(TIMEOUT)
                    .block();
            log.info("[TIKTOK] Post removed: postId={}", externalPostId);
        } catch (WebClientResponseException ex) {
            throw new SocialApiException("TIKTOK", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }
}

// ─── Twitter / X ──────────────────────────────────────────

@Component
class TwitterAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final Logger log = LoggerFactory.getLogger(TwitterAdapter.class);
    private final String apiKey;
    private final String apiSecret;

    TwitterAdapter(@Value("${social.twitter.api-key}")    String apiKey,
                   @Value("${social.twitter.api-secret}") String apiSecret) {
        this.apiKey    = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public Platform getPlatform() { return Platform.TWITTER; }

    @Override
    public String publish(String accountId, String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[TWITTER] Publishing tweet with OAuth 1.0a");
        String text = buildTweetText(title, description, mediaUrl);

        try {
            String endpoint = "https://api.twitter.com/2/tweets";
            String body     = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";

            String oauthHeader = buildOAuthHeader("POST", endpoint, accessToken, tokenSecret);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",  "application/json");
            conn.setRequestProperty("Authorization", oauthHeader);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);

            try (var out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status >= 400) {
                String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new SocialApiException("TWITTER", "HTTP " + status + ": " + error);
            }

            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String tweetId = mapper.readTree(response).get("data").get("id").asText();
            log.info("[TWITTER] Tweet published: id={}", tweetId);
            return tweetId;

        } catch (SocialApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SocialApiException("TWITTER", ex.getMessage(), ex);
        }
    }

    @Override
    public void remove(String accessToken, String externalPostId) {
        log.info("[TWITTER] Deleting tweet: id={}", externalPostId);
        try {
            String endpoint    = "https://api.twitter.com/2/tweets/" + externalPostId;
            String oauthHeader = buildOAuthHeader("DELETE", endpoint, accessToken, externalPostId);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", oauthHeader);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);

            int status = conn.getResponseCode();
            if (status >= 400) {
                String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new SocialApiException("TWITTER", "HTTP " + status + ": " + error);
            }
            log.info("[TWITTER] Tweet deleted: id={}", externalPostId);

        } catch (SocialApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SocialApiException("TWITTER", ex.getMessage(), ex);
        }
    }

    // ─── OAuth 1.0a signing ───────────────────────────────

    private String buildOAuthHeader(String method, String url,
                                    String accessToken, String tokenSecret) throws Exception {
        String nonce     = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        TreeMap<String, String> params = new TreeMap<>();
        params.put("oauth_consumer_key",     apiKey);
        params.put("oauth_nonce",            nonce);
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp",        timestamp);
        params.put("oauth_token",            accessToken);
        params.put("oauth_version",          "1.0");

        String paramString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        String baseString = method + "&" + encode(url) + "&" + encode(paramString);
        String signingKey = encode(apiSecret) + "&" + encode(tokenSecret);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));

        params.put("oauth_signature", signature);

        return "OAuth " + params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=\"" + encode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String buildTweetText(String title, String description, String mediaUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append(title, 0, Math.min(title.length(), 200));
        if (description != null && !description.isBlank()) {
            sb.append("\n\n").append(description, 0, Math.min(description.length(), 80));
        }
        sb.append("\n").append(mediaUrl);
        return sb.length() > 280 ? sb.substring(0, 280) : sb.toString();
    }
}

// ─── Instagram ────────────────────────────────────────────

@Component
class InstagramAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final Logger log = LoggerFactory.getLogger(InstagramAdapter.class);
    private final WebClient client;

    InstagramAdapter(WebClient.Builder builder,
                     @Value("${social.instagram.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Platform getPlatform() { return Platform.INSTAGRAM; }

    @Override
    public String publish(String accountId, String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[INSTAGRAM] Publishing media for igUserId={}", accountId);
        try {
            String caption = buildCaption(title, description);
            String igUserId = accountId;

            @SuppressWarnings("unchecked")
            Map<?, ?> containerResponse = client.post()
                    .uri(ub -> ub.path("/" + igUserId + "/media")
                            .queryParam("image_url", mediaUrl)
                            .queryParam("caption", caption)
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (containerResponse == null || !containerResponse.containsKey("id"))
                throw new SocialApiException("INSTAGRAM", "No container id from Instagram");

            String containerId = String.valueOf(containerResponse.get("id"));

            @SuppressWarnings("unchecked")
            Map<?, ?> publishResponse = client.post()
                    .uri(ub -> ub.path("/" + igUserId + "/media_publish")
                            .queryParam("creation_id", containerId)
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (publishResponse == null || !publishResponse.containsKey("id"))
                throw new SocialApiException("INSTAGRAM", "No post id from Instagram publish");

            String postId = String.valueOf(publishResponse.get("id"));
            log.info("[INSTAGRAM] Media published: id={}", postId);
            return postId;

        } catch (WebClientResponseException ex) {
            throw new SocialApiException("INSTAGRAM", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public void remove(String accessToken, String externalPostId) {
        log.warn("[INSTAGRAM] Post deletion via API is not supported for standard apps. " +
                "Post id={} will be marked as REMOVED locally only.", externalPostId);
    }

    private String buildCaption(String title, String description) {
        StringBuilder sb = new StringBuilder(title);
        if (description != null && !description.isBlank()) sb.append("\n\n").append(description);
        return sb.length() > 2200 ? sb.substring(0, 2200) : sb.toString();
    }
}

// ─── YouTube ──────────────────────────────────────────────

@Component
class YouTubeAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final Logger log = LoggerFactory.getLogger(YouTubeAdapter.class);
    private final WebClient client;

    YouTubeAdapter(WebClient.Builder builder,
                   @Value("${social.youtube.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Platform getPlatform() { return Platform.YOUTUBE; }

    @Override
    public String publish(String accountId, String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[YOUTUBE] Publishing video");
        try {
            Map<String, Object> body = Map.of(
                    "snippet", Map.of(
                            "title",       title.substring(0, Math.min(title.length(), 100)),
                            "description", description != null ? description.substring(0, Math.min(description.length(), 5000)) : "",
                            "categoryId",  "22"
                    ),
                    "status", Map.of("privacyStatus", "private")
            );

            @SuppressWarnings("unchecked")
            Map<?, ?> response = client.post()
                    .uri(ub -> ub.path("/videos").queryParam("part", "snippet,status").build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null || !response.containsKey("id"))
                throw new SocialApiException("YOUTUBE", "No video id from YouTube response");

            String videoId = String.valueOf(response.get("id"));
            log.info("[YOUTUBE] Video published: id={}", videoId);
            return videoId;

        } catch (WebClientResponseException ex) {
            throw new SocialApiException("YOUTUBE", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public void remove(String accessToken, String externalPostId) {
        log.info("[YOUTUBE] Deleting video: id={}", externalPostId);
        try {
            client.delete()
                    .uri(ub -> ub.path("/videos").queryParam("id", externalPostId).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(TIMEOUT)
                    .block();
            log.info("[YOUTUBE] Video deleted: id={}", externalPostId);
        } catch (WebClientResponseException ex) {
            throw new SocialApiException("YOUTUBE", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }
}