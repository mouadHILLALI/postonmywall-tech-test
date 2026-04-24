package com.postonmywall.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postonmywall.common.Platform;
import com.postonmywall.exception.SocialApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TwitterAdapter extends BaseAdapter implements SocialMediaAdapter {

    private static final int MAX_TWEET_LENGTH   = 280;
    private static final int MAX_TITLE_IN_TWEET = 200;
    private static final int MAX_DESC_IN_TWEET  = 80;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String apiSecret;

    public TwitterAdapter(@Value("${social.twitter.api-key}")    String apiKey,
                          @Value("${social.twitter.api-secret}") String apiSecret) {
        this.apiKey    = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public Platform getPlatform() { return Platform.TWITTER; }

    @Override
    public String publish(String accountId, String accessToken, String tokenSecret,
                          String mediaUrl, String title, String description) {
        log.info("[TWITTER] Publishing tweet");
        String text = buildTweetText(title, description, mediaUrl);

        try {
            String endpoint = "https://api.twitter.com/2/tweets";
            String body     = MAPPER.writeValueAsString(Map.of("text", text));

            String authHeader = (tokenSecret == null || tokenSecret.isBlank())
                    ? "Bearer " + accessToken
                    : buildOAuthHeader("POST", endpoint, accessToken, tokenSecret);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",  "application/json");
            conn.setRequestProperty("Authorization", authHeader);
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
            String tweetId  = MAPPER.readTree(response).get("data").get("id").asText();
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
            String endpoint = "https://api.twitter.com/2/tweets/" + externalPostId;

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
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

    // ── OAuth 1.0a signing ────────────────────────────────────────

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

        String baseString  = method + "&" + encode(url) + "&" + encode(paramString);
        String signingKey  = encode(apiSecret) + "&" + encode(tokenSecret);

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
        if (title != null && !title.isBlank())
            sb.append(title, 0, Math.min(title.length(), MAX_TITLE_IN_TWEET));
        if (description != null && !description.isBlank())
            sb.append("\n\n").append(description, 0, Math.min(description.length(), MAX_DESC_IN_TWEET));
        sb.append("\n").append(mediaUrl);
        return sb.length() > MAX_TWEET_LENGTH ? sb.substring(0, MAX_TWEET_LENGTH) : sb.toString();
    }
}
