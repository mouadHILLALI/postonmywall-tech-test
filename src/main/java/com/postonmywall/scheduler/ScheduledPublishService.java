package com.postonmywall.scheduler;

import com.postonmywall.account.SocialAccount;
import com.postonmywall.account.SocialAccountService;
import com.postonmywall.auth.User;
import com.postonmywall.auth.UserRepository;
import com.postonmywall.common.Frequency;
import com.postonmywall.exception.ResourceNotFoundException;
import com.postonmywall.file.MediaFile;
import com.postonmywall.file.MediaFileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ScheduledPublishService {

    private final ScheduledPublishRepository scheduledPublishRepository;
    private final MediaFileService mediaFileService;
    private final SocialAccountService socialAccountService;
    private final UserRepository userRepository;

    public ScheduledPublishService(ScheduledPublishRepository scheduledPublishRepository,
                                   MediaFileService mediaFileService,
                                   SocialAccountService socialAccountService,
                                   UserRepository userRepository) {
        this.scheduledPublishRepository = scheduledPublishRepository;
        this.mediaFileService = mediaFileService;
        this.socialAccountService = socialAccountService;
        this.userRepository = userRepository;
    }

    public ScheduledPublishResponse create(UUID userId, CreateScheduleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MediaFile mediaFile = mediaFileService.getActiveFile(userId, request.getMediaFileId());
        SocialAccount socialAccount = socialAccountService.getActiveAccount(userId, request.getSocialAccountId());

        ScheduledPublish job = ScheduledPublish.builder()
                .user(user)
                .mediaFile(mediaFile)
                .socialAccount(socialAccount)
                .title(request.getTitle())
                .description(request.getDescription())
                .frequency(request.getFrequency())
                .build();

        return toResponse(scheduledPublishRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<ScheduledPublishResponse> list(UUID userId) {
        return scheduledPublishRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ScheduledPublishResponse cancel(UUID userId, UUID jobId) {
        ScheduledPublish job = scheduledPublishRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled job not found: " + jobId));
        job.setActive(false);
        return toResponse(scheduledPublishRepository.save(job));
    }

    public List<ScheduledPublish> getActiveJobsByFrequency(Frequency frequency) {
        return scheduledPublishRepository.findAllByActiveTrueAndFrequency(frequency);
    }

    private ScheduledPublishResponse toResponse(ScheduledPublish job) {
        return new ScheduledPublishResponse(
                job.getId(),
                job.getMediaFile().getId(),
                job.getSocialAccount().getId(),
                job.getTitle(),
                job.getFrequency(),
                job.isActive(),
                job.getCreatedAt()
        );
    }
}
