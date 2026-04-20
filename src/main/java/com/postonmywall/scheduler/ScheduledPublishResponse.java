package com.postonmywall.scheduler;

import com.postonmywall.common.Frequency;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ScheduledPublishResponse {
    private UUID id;
    private UUID mediaFileId;
    private UUID socialAccountId;
    private String title;
    private Frequency frequency;
    private boolean active;
    private Instant createdAt;
}
