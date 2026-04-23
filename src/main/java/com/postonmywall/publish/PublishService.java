package com.postonmywall.publish;

import com.postonmywall.account.SocialAccount;
import com.postonmywall.account.SocialAccountService;
import com.postonmywall.auth.User;
import com.postonmywall.auth.UserRepository;
import com.postonmywall.common.Platform;
import com.postonmywall.common.PublishStatus;
import com.postonmywall.exception.BusinessException;
import com.postonmywall.exception.ResourceNotFoundException;
import com.postonmywall.exception.SocialApiException;
import com.postonmywall.file.MediaFile;
import com.postonmywall.file.MediaFileService;
import com.postonmywall.file.S3Service;
import com.postonmywall.scheduler.ScheduledPublish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);

    private final PublishLogRepository publishLogRepository;
    private final MediaFileService mediaFileService;
    private final SocialAccountService socialAccountService;
    private final S3Service s3Service;
    private final SocialAdapterRegistry adapterRegistry;
    private final UserRepository userRepository;
    private final GoogleTokenRefresher googleTokenRefresher;

    public PublishService(PublishLogRepository publishLogRepository,
                          MediaFileService mediaFileService,
                          SocialAccountService socialAccountService,
                          S3Service s3Service,
                          SocialAdapterRegistry adapterRegistry,
                          UserRepository userRepository,
                          GoogleTokenRefresher googleTokenRefresher) {
        this.publishLogRepository = publishLogRepository;
        this.mediaFileService = mediaFileService;
        this.socialAccountService = socialAccountService;
        this.s3Service = s3Service;
        this.adapterRegistry = adapterRegistry;
        this.userRepository = userRepository;
        this.googleTokenRefresher = googleTokenRefresher;
    }

    public PublishResponse publish(UUID userId, PublishRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MediaFile mediaFile = mediaFileService.getActiveFile(userId, request.getMediaFileId());
        SocialAccount socialAccount = socialAccountService.getActiveAccount(userId, request.getSocialAccountId());

        PublishLog publishLog = PublishLog.builder()
                .user(user)
                .mediaFile(mediaFile)
                .socialAccount(socialAccount)
                .platform(socialAccount.getPlatform())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(PublishStatus.PENDING)
                .build();

        publishLogRepository.save(publishLog);

        try {
            String mediaUrl = s3Service.generatePresignedUrl(mediaFile.getS3Key(), 30);
            SocialMediaAdapter adapter = adapterRegistry.get(socialAccount.getPlatform());

            String externalPostId;
            try {
                externalPostId = adapter.publish(
                        socialAccount.getAccountId(),
                        socialAccount.getAccessToken(),
                        socialAccount.getTokenSecret(),
                        mediaUrl,
                        request.getTitle(),
                        request.getDescription()
                );
            } catch (SocialApiException ex) {
                if (isTokenExpired(ex) && socialAccount.getPlatform() == Platform.YOUTUBE
                        && socialAccount.getTokenSecret() != null) {
                    String newToken = googleTokenRefresher.refresh(socialAccount.getTokenSecret());
                    socialAccountService.updateAccessToken(socialAccount.getId(), newToken);
                    externalPostId = adapter.publish(
                            socialAccount.getAccountId(),
                            newToken,
                            socialAccount.getTokenSecret(),
                            mediaUrl,
                            request.getTitle(),
                            request.getDescription()
                    );
                } else {
                    throw ex;
                }
            }

            publishLog.setExternalPostId(externalPostId);
            publishLog.setStatus(PublishStatus.PUBLISHED);
            publishLog.setPublishedAt(Instant.now());

        } catch (Exception ex) {
            log.error("Failed to publish log id={}", publishLog.getId(), ex);
            publishLog.setStatus(PublishStatus.FAILED);
            String msg = ex.getMessage();
            publishLog.setErrorMessage(msg != null ? msg.substring(0, Math.min(msg.length(), 1000)) : "Unknown error");
        }

        return toResponse(publishLogRepository.save(publishLog));
    }

    public PublishResponse remove(UUID userId, UUID logId) {
        PublishLog publishLog = publishLogRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Publish log not found: " + logId));

        if (publishLog.getStatus() != PublishStatus.PUBLISHED)
            throw new BusinessException("Only PUBLISHED posts can be removed (current: " + publishLog.getStatus() + ")");

        String externalPostId = publishLog.getExternalPostId();
        if (externalPostId == null)
            throw new BusinessException("No external post ID recorded — cannot remove from platform");

        try {
            adapterRegistry.get(publishLog.getPlatform())
                    .remove(publishLog.getSocialAccount().getAccessToken(), externalPostId);

            publishLog.setStatus(PublishStatus.REMOVED);
            publishLog.setRemovedAt(Instant.now());

        } catch (Exception ex) {
            log.error("Failed to remove post id={}", publishLog.getId(), ex);
            String msg = ex.getMessage();
            publishLog.setErrorMessage(msg != null ? msg.substring(0, Math.min(msg.length(), 1000)) : "Unknown error");
        }

        return toResponse(publishLogRepository.save(publishLog));
    }

    @Transactional(readOnly = true)
    public Page<PublishResponse> listLogs(UUID userId, Pageable pageable) {
        return publishLogRepository.findAllByUserId(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PublishResponse getLog(UUID userId, UUID logId) {
        return toResponse(publishLogRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Publish log not found: " + logId)));
    }

    /** Called by the scheduler for recurring publish jobs. */
    public void publishScheduled(List<ScheduledPublish> jobs) {
        jobs.forEach(job -> {
            try {
                PublishRequest request = new PublishRequest();
                request.setMediaFileId(job.getMediaFile().getId());
                request.setSocialAccountId(job.getSocialAccount().getId());
                request.setTitle(job.getTitle() != null ? job.getTitle() : "Scheduled post");
                request.setDescription(job.getDescription());
                publish(job.getUser().getId(), request);
            } catch (Exception ex) {
                log.error("Scheduled publish failed for job id={}", job.getId(), ex);
            }
        });
    }

    private boolean isTokenExpired(SocialApiException ex) {
        String msg = ex.getMessage();
        return msg != null && (msg.contains("HTTP 401") || msg.contains("UNAUTHENTICATED"));
    }

    private PublishResponse toResponse(PublishLog log) {
        return new PublishResponse(
                log.getId(),
                log.getPlatform(),
                log.getMediaFile().getId(),
                log.getSocialAccount().getId(),
                log.getExternalPostId(),
                log.getTitle(),
                log.getStatus(),
                log.getErrorMessage(),
                log.getPublishedAt(),
                log.getRemovedAt(),
                log.getCreatedAt()
        );
    }
}
