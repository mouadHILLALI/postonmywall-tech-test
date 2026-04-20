package com.postonmywall.file;

import com.postonmywall.common.FileStatus;
import com.postonmywall.common.MediaType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MediaFileResponse {
    private UUID id;
    private String originalName;
    private MediaType mediaType;
    private long sizeBytes;
    private FileStatus status;
    private String presignedUrl;
    private Instant createdAt;
}
