package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import com.postonmywall.exception.SocialApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class YouTubeAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final int      MAX_TITLE_LENGTH       = 100;
    private static final int      MAX_DESCRIPTION_LENGTH = 5000;
    private static final Duration UPLOAD_TIMEOUT         = Duration.ofMinutes(30);

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
            String sessionUrl = initiateResumableUpload(accessToken, title, description);
            String videoId    = streamFromS3ToYouTube(sessionUrl, mediaUrl);
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

    // Step 1 — POST metadata, get back a session URL in the Location header
    private String initiateResumableUpload(String accessToken, String title, String description) {
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

        String sessionUrl = WebClient.builder().build().post()
                .uri("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Upload-Content-Type", "video/mp4")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .map(resp -> resp.getHeaders().getFirst(HttpHeaders.LOCATION))
                .timeout(TIMEOUT)
                .block();

        if (sessionUrl == null)
            throw new SocialApiException("YOUTUBE", "No upload session URL returned by YouTube");

        log.debug("[YOUTUBE] Resumable upload session: {}", sessionUrl);
        return sessionUrl;
    }

    // Step 2 — stream the video from S3 directly into the YouTube session URL
    private String streamFromS3ToYouTube(String sessionUrl, String s3Url) {
        WebClient generic = WebClient.builder().build();

        Flux<DataBuffer> videoStream = generic.get()
                .uri(URI.create(s3Url))
                .retrieve()
                .bodyToFlux(DataBuffer.class);

        @SuppressWarnings("unchecked")
        Map<?, ?> response = generic.put()
                .uri(sessionUrl)
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(BodyInserters.fromDataBuffers(videoStream))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(UPLOAD_TIMEOUT)
                .block();

        if (response == null || !response.containsKey("id"))
            throw new SocialApiException("YOUTUBE", "No video id in YouTube upload response");

        return String.valueOf(response.get("id"));
    }
}
