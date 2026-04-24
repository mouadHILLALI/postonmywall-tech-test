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

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class YouTubeAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final int MAX_TITLE_LENGTH       = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;

    private final WebClient client;

    public YouTubeAdapter(WebClient.Builder builder,
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
                            "title",       title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH)),
                            "description", description != null
                                    ? description.substring(0, Math.min(description.length(), MAX_DESCRIPTION_LENGTH))
                                    : "",
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
