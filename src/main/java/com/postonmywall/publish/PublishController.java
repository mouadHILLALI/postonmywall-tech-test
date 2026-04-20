package com.postonmywall.publish;

import com.postonmywall.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/publish")
@Tag(name = "Publish", description = "Publish and remove posts on social media platforms")
public class PublishController {

    private final PublishService publishService;

    public PublishController(PublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping
    @Operation(summary = "Publish a media file to a social platform in one click")
    public ResponseEntity<ApiResponse<PublishResponse>> publish(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody PublishRequest request) {

        PublishResponse response = publishService.publish(userId(principal), request);
        HttpStatus status = response.getStatus().name().equals("PUBLISHED")
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }

    @DeleteMapping("/{logId}")
    @Operation(summary = "Remove a published post from its social platform")
    public ResponseEntity<ApiResponse<PublishResponse>> remove(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID logId) {

        PublishResponse response = publishService.remove(userId(principal), logId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Post removal initiated"));
    }

    @GetMapping
    @Operation(summary = "List all publish logs for the authenticated user (paginated)")
    public ResponseEntity<ApiResponse<Page<PublishResponse>>> listLogs(
            @AuthenticationPrincipal UserDetails principal,
            Pageable pageable) {

        Page<PublishResponse> page = publishService.listLogs(userId(principal), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{logId}")
    @Operation(summary = "Get a single publish log by ID")
    public ResponseEntity<ApiResponse<PublishResponse>> getLog(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID logId) {

        PublishResponse response = publishService.getLog(userId(principal), logId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID userId(UserDetails principal) {
        return UUID.fromString(principal.getUsername());
    }
}
