package com.postonmywall.publish;

import com.postonmywall.exception.SocialApiException;

import java.time.Duration;
import java.util.Map;

abstract class BaseAdapter {

    protected static final Duration TIMEOUT = Duration.ofSeconds(15);

    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractData(Map<?, ?> response, String platform) {
        if (response == null)
            throw new SocialApiException(platform, "Empty response from API");
        Object data = response.get("data");
        if (!(data instanceof Map))
            throw new SocialApiException(platform, "Missing 'data' field in response");
        return (Map<String, Object>) data;
    }
}
