package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import com.postonmywall.exception.SocialApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Component
public class InstagramAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final int MAX_CAPTION_LENGTH = 2200;

    private final WebClient client;

    public InstagramAdapter(WebClient.Builder builder,
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

            @SuppressWarnings("unchecked")
            Map<?, ?> containerResponse = client.post()
                    .uri(ub -> ub.path("/" + accountId + "/media")
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
                    .uri(ub -> ub.path("/" + accountId + "/media_publish")
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
        // Instagram Graph API does not support post deletion for standard apps.
        log.warn("[INSTAGRAM] Post deletion is not supported by the Instagram Graph API. " +
                "Post id={} will be marked as REMOVED locally only.", externalPostId);
    }

    private String buildCaption(String title, String description) {
        StringBuilder sb = new StringBuilder(title);
        if (description != null && !description.isBlank()) sb.append("\n\n").append(description);
        return sb.length() > MAX_CAPTION_LENGTH ? sb.substring(0, MAX_CAPTION_LENGTH) : sb.toString();
    }
}
