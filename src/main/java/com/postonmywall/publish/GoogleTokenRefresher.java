package com.postonmywall.publish;

import com.postonmywall.exception.SocialApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Component
public class GoogleTokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenRefresher.class);

    private final WebClient client;
    private final String clientId;
    private final String clientSecret;

    public GoogleTokenRefresher(
            WebClient.Builder builder,
            @Value("${social.youtube.client-id}")     String clientId,
            @Value("${social.youtube.client-secret}") String clientSecret) {
        this.client       = builder.baseUrl("https://oauth2.googleapis.com").build();
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
    }

    public String refresh(String refreshToken) {
        log.info("[YOUTUBE] Refreshing access token");
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id",     clientId);
            form.add("client_secret", clientSecret);
            form.add("refresh_token", refreshToken);
            form.add("grant_type",    "refresh_token");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("access_token"))
                throw new SocialApiException("YOUTUBE", "Token refresh returned no access_token");

            String newToken = (String) response.get("access_token");
            log.info("[YOUTUBE] Access token refreshed successfully");
            return newToken;

        } catch (WebClientResponseException ex) {
            throw new SocialApiException("YOUTUBE",
                    "Token refresh failed: HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }
}
