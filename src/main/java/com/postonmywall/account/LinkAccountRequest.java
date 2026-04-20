package com.postonmywall.account;

import com.postonmywall.common.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkAccountRequest {
    @NotNull  private Platform platform;
    @NotBlank private String accountId;
    @NotBlank private String accessToken;
    private String tokenSecret;
}
