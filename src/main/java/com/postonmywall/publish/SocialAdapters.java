package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import com.postonmywall.exception.SocialApiException;
import com.postonmywall.publish.SocialMediaAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

// ─── Shared constants ─────────────────────────────────────

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
    public String publish(String accessToken, String tokenSecret,
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
    private final WebClient client;

    TwitterAdapter(WebClient.Builder builder,
                   @Value("${social.twitter.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Platform getPlatform() { return Platform.TWITTER; }

    @Override
    public String publish(String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[TWITTER] Publishing tweet");
        String text = buildTweetText(title, description, mediaUrl);
        try {
            @SuppressWarnings("unchecked")
            Map<?, ?> response = client.post()
                    .uri("/tweets")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            Map<String, Object> data = extractData(response, "TWITTER");
            String tweetId = String.valueOf(data.get("id"));
            log.info("[TWITTER] Tweet published: id={}", tweetId);
            return tweetId;

        } catch (WebClientResponseException ex) {
            throw new SocialApiException("TWITTER", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public void remove(String accessToken, String externalPostId) {
        log.info("[TWITTER] Deleting tweet: id={}", externalPostId);
        try {
            client.delete()
                    .uri("/tweets/" + externalPostId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(TIMEOUT)
                    .block();
            log.info("[TWITTER] Tweet deleted: id={}", externalPostId);
        } catch (WebClientResponseException ex) {
            throw new SocialApiException("TWITTER", "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
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
    public String publish(String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[INSTAGRAM] Publishing media");
        try {
            String caption = buildCaption(title, description);

            // Step 1: Create media container
            @SuppressWarnings("unchecked")
            Map<?, ?> containerResponse = client.post()
                    .uri(ub -> ub.path("/me/media")
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

            // Step 2: Publish the container
            @SuppressWarnings("unchecked")
            Map<?, ?> publishResponse = client.post()
                    .uri(ub -> ub.path("/me/media_publish")
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
        // Instagram Graph API does not support post deletion for most app types.
        // We log and let PublishService mark the record as REMOVED in the DB.
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
    public String publish(String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[YOUTUBE] Publishing video");
        try {
            Map<String, Object> body = Map.of(
                "snippet", Map.of(
                    "title", title.substring(0, Math.min(title.length(), 100)),
                    "description", description != null ? description.substring(0, Math.min(description.length(), 5000)) : "",
                    "categoryId", "22"
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
