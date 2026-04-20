package com.postonmywall.account;

import com.postonmywall.common.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SocialAccountResponse {
    private UUID id;
    private Platform platform;
    private String accountId;
    private boolean active;
    private Instant createdAt;
}
