package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import com.postonmywall.exception.SocialApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Component
public class TikTokAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final int MAX_TITLE_LENGTH = 150;

    private final WebClient client;

    public TikTokAdapter(WebClient.Builder builder,
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
                            "title", title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH)),
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
