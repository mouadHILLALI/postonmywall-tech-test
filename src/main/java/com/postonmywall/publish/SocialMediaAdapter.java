package com.postonmywall.publish;

import com.postonmywall.common.Platform;

/**
 * Contract that every social platform adapter must implement.
 * Adapters are registered automatically via SocialAdapterRegistry.
 */
public interface SocialMediaAdapter {

    Platform getPlatform();

    /**
     * Publish a post to the platform.
     *
     * @param accessToken  OAuth access token for the account
     * @param tokenSecret  OAuth1 token secret (nullable — only Twitter v1 needs it)
     * @param mediaUrl     Pre-signed S3 URL pointing to the media file
     * @param title        Post title / caption
     * @param description  Extended description (optional)
     * @return             External post ID returned by the platform
     */
    String publish(String accountId, String accessToken, String tokenSecret,
                   String mediaUrl, String title, String description);

    /**
     * Remove / delete a post from the platform.
     *
     * @param accessToken    OAuth access token
     * @param externalPostId The post ID previously returned by publish()
     */
    void remove(String accessToken, String externalPostId);
}
