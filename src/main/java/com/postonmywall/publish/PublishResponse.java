package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import com.postonmywall.common.PublishStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class PublishResponse {
    private UUID id;
    private Platform platform;
    private UUID mediaFileId;
    private UUID socialAccountId;
    private String externalPostId;
    private String title;
    private PublishStatus status;
    private String errorMessage;
    private Instant publishedAt;
    private Instant removedAt;
    private Instant createdAt;
}
