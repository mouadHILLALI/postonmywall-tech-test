package com.postonmywall.file;

import com.postonmywall.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Media Files", description = "Upload and manage media files stored in S3")
public class MediaFileController {

    private final MediaFileService mediaFileService;

    public MediaFileController(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    /** Public endpoint — no JWT required. Used by Instagram to download media for publishing. */
    @GetMapping("/{fileId}/stream")
    public ResponseEntity<StreamingResponseBody> streamFile(@PathVariable UUID fileId) {
        MediaFile file = mediaFileService.findById(fileId);
        var s3Object = mediaFileService.getS3Service().getObject(file.getS3Key());
        String contentType = contentTypeFromFilename(file.getOriginalName());
        StreamingResponseBody body = out -> {
            try (s3Object) { s3Object.transferTo(out); }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "inline; filename=\"" + file.getOriginalName() + "\"")
                .body(body);
    }

    private static String contentTypeFromFilename(String name) {
        if (name == null) return "application/octet-stream";
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png"))  return "image/png";
        if (n.endsWith(".gif"))  return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".mp4"))  return "video/mp4";
        if (n.endsWith(".mov"))  return "video/quicktime";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp3"))  return "audio/mpeg";
        return "application/octet-stream";
    }

    @PostMapping("/initiate")
    @Operation(summary = "Get a presigned S3 PUT URL for direct browser-to-S3 upload")
    public ResponseEntity<ApiResponse<InitiateUploadResponse>> initiateUpload(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody InitiateUploadRequest request) {

        InitiateUploadResponse response = mediaFileService.initiateUpload(userId(principal), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm a completed S3 upload and persist the file metadata")
    public ResponseEntity<ApiResponse<MediaFileResponse>> confirmUpload(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ConfirmUploadRequest request) {

        MediaFileResponse response = mediaFileService.confirmUpload(userId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "File uploaded successfully"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a media file (image / video / audio) to S3")
    public ResponseEntity<ApiResponse<MediaFileResponse>> upload(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam("file") MultipartFile file) throws IOException {

        MediaFileResponse response = mediaFileService.upload(userId(principal), file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "File uploaded successfully"));
    }

    @GetMapping
    @Operation(summary = "List all uploaded media files (paginated)")
    public ResponseEntity<ApiResponse<Page<MediaFileResponse>>> listFiles(
            @AuthenticationPrincipal UserDetails principal,
            Pageable pageable) {

        Page<MediaFileResponse> page = mediaFileService.listFiles(userId(principal), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Get a single media file with a fresh pre-signed S3 URL")
    public ResponseEntity<ApiResponse<MediaFileResponse>> getFile(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID fileId) {

        MediaFileResponse response = mediaFileService.getFile(userId(principal), fileId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Delete a media file (removes from S3 and marks inactive in DB)")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID fileId) {

        mediaFileService.deleteFile(userId(principal), fileId);
        return ResponseEntity.ok(ApiResponse.ok("File deleted successfully"));
    }

    private UUID userId(UserDetails principal) {
        return UUID.fromString(principal.getUsername());
    }
}
