package com.postonmywall.publish;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class PublishRequest {
    @NotNull  private UUID mediaFileId;
    @NotNull  private UUID socialAccountId;
    @NotBlank private String title;
    private String description;
}
