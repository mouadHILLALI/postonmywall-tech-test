package com.postonmywall.scheduler;

import com.postonmywall.common.Frequency;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateScheduleRequest {
    @NotNull private UUID mediaFileId;
    @NotNull private UUID socialAccountId;
    private String title;
    private String description;
    @NotNull private Frequency frequency;
}
