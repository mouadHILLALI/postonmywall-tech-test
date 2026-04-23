package com.postonmywall.file;

import com.postonmywall.auth.User;
import com.postonmywall.auth.UserRepository;
import com.postonmywall.common.FileStatus;
import com.postonmywall.common.MediaType;
import com.postonmywall.exception.BusinessException;
import com.postonmywall.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class MediaFileService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm");
    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of("audio/mpeg", "audio/wav", "audio/ogg", "audio/aac");
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024; // 500 MB

    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    public MediaFileService(MediaFileRepository mediaFileRepository,
                            UserRepository userRepository,
                            S3Service s3Service) {
        this.mediaFileRepository = mediaFileRepository;
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    public InitiateUploadResponse initiateUpload(UUID userId, InitiateUploadRequest request) {
        if (request.getSizeBytes() > MAX_FILE_SIZE)
            throw new BusinessException("File exceeds maximum allowed size of 500 MB");

        String contentType = request.getContentType();
        if (contentType == null) throw new BusinessException("Content type is required");
        resolveMediaType(contentType); // validates allowed types

        String s3Key    = s3Service.generateKey(userId, request.getFilename());
        String uploadUrl = s3Service.generatePresignedPutUrl(s3Key, contentType);
        return new InitiateUploadResponse(uploadUrl, s3Key);
    }

    public MediaFileResponse confirmUpload(UUID userId, ConfirmUploadRequest request) {
        String expectedPrefix = "media/" + userId + "/";
        if (!request.getS3Key().startsWith(expectedPrefix))
            throw new BusinessException("Invalid S3 key");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MediaType mediaType = resolveMediaType(request.getContentType());

        MediaFile mediaFile = MediaFile.builder()
                .user(user)
                .s3Key(request.getS3Key())
                .originalName(request.getOriginalName())
                .mediaType(mediaType)
                .sizeBytes(request.getSizeBytes())
                .build();

        MediaFile saved = mediaFileRepository.save(mediaFile);
        return toResponse(saved, s3Service.generatePresignedUrl(saved.getS3Key()));
    }

    public MediaFileResponse upload(UUID userId, MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BusinessException("File must not be empty");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("File exceeds maximum allowed size of 500 MB");

        String contentType = file.getContentType();
        if (contentType == null) throw new BusinessException("Content type is required");

        MediaType mediaType = resolveMediaType(contentType);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String s3Key = s3Service.upload(file, userId);

        MediaFile mediaFile = MediaFile.builder()
                .user(user)
                .s3Key(s3Key)
                .originalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                .mediaType(mediaType)
                .sizeBytes(file.getSize())
                .build();

        MediaFile saved = mediaFileRepository.save(mediaFile);
        return toResponse(saved, s3Service.generatePresignedUrl(s3Key));
    }

    @Transactional(readOnly = true)
    public Page<MediaFileResponse> listFiles(UUID userId, Pageable pageable) {
        return mediaFileRepository.findAllByUserIdAndStatus(userId, FileStatus.ACTIVE, pageable)
                .map(f -> toResponse(f, null));
    }

    @Transactional(readOnly = true)
    public MediaFileResponse getFile(UUID userId, UUID fileId) {
        MediaFile file = mediaFileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Media file not found: " + fileId));
        return toResponse(file, s3Service.generatePresignedUrl(file.getS3Key()));
    }

    public void deleteFile(UUID userId, UUID fileId) {
        MediaFile file = mediaFileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Media file not found: " + fileId));
        s3Service.delete(file.getS3Key());
        file.setStatus(FileStatus.INACTIVE);
        mediaFileRepository.save(file);
    }

    // Used internally by PublishService
    public MediaFile getActiveFile(UUID userId, UUID fileId) {
        return mediaFileRepository.findByIdAndUserId(fileId, userId)
                .filter(f -> f.getStatus() == FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active media file not found: " + fileId));
    }

    private MediaType resolveMediaType(String contentType) {
        if (ALLOWED_IMAGE_TYPES.contains(contentType)) return MediaType.IMAGE;
        if (ALLOWED_VIDEO_TYPES.contains(contentType)) return MediaType.VIDEO;
        if (ALLOWED_AUDIO_TYPES.contains(contentType)) return MediaType.AUDIO;
        throw new BusinessException("Unsupported file type: " + contentType);
    }

    private MediaFileResponse toResponse(MediaFile file, String presignedUrl) {
        return new MediaFileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getMediaType(),
                file.getSizeBytes(),
                file.getStatus(),
                presignedUrl,
                file.getCreatedAt()
        );
    }
}
