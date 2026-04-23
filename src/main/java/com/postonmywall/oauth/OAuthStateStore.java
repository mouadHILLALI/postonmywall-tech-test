package com.postonmywall.oauth;

import com.postonmywall.common.Platform;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthStateStore {

    public record OAuthState(
            UUID userId,
            Platform platform,
            String frontendRedirectUri,
            String codeVerifier,
            Instant expiresAt
    ) {}

    private final ConcurrentHashMap<String, OAuthState> store = new ConcurrentHashMap<>();

    public String save(UUID userId, Platform platform, String frontendRedirectUri, String codeVerifier) {
        String state = UUID.randomUUID().toString().replace("-", "");
        store.put(state, new OAuthState(userId, platform, frontendRedirectUri, codeVerifier, Instant.now().plusSeconds(600)));
        return state;
    }

    /** Retrieve and remove — single-use. Returns empty if expired or unknown. */
    public Optional<OAuthState> consume(String state) {
        OAuthState s = store.remove(state);
        if (s == null || s.expiresAt().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(s);
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
