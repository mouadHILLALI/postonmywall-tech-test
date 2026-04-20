package com.postonmywall.scheduler;

import com.postonmywall.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@Tag(name = "Scheduler", description = "Create and manage recurring auto-publish jobs")
public class SchedulerController {

    private final ScheduledPublishService scheduledPublishService;

    public SchedulerController(ScheduledPublishService scheduledPublishService) {
        this.scheduledPublishService = scheduledPublishService;
    }

    @PostMapping
    @Operation(summary = "Create a recurring publish schedule (DAILY or WEEKLY)")
    public ResponseEntity<ApiResponse<ScheduledPublishResponse>> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateScheduleRequest request) {

        ScheduledPublishResponse response = scheduledPublishService.create(userId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Schedule created successfully"));
    }

    @GetMapping
    @Operation(summary = "List all scheduled publish jobs for the authenticated user")
    public ResponseEntity<ApiResponse<List<ScheduledPublishResponse>>> list(
            @AuthenticationPrincipal UserDetails principal) {

        List<ScheduledPublishResponse> jobs = scheduledPublishService.list(userId(principal));
        return ResponseEntity.ok(ApiResponse.ok(jobs));
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "Cancel (deactivate) a scheduled publish job")
    public ResponseEntity<ApiResponse<ScheduledPublishResponse>> cancel(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID jobId) {

        ScheduledPublishResponse response = scheduledPublishService.cancel(userId(principal), jobId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Schedule cancelled successfully"));
    }

    private UUID userId(UserDetails principal) {
        return UUID.fromString(principal.getUsername());
    }
}
