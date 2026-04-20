package com.postonmywall.publish;

import com.postonmywall.account.SocialAccount;
import com.postonmywall.account.SocialAccountService;
import com.postonmywall.auth.User;
import com.postonmywall.auth.UserRepository;
import com.postonmywall.common.FileStatus;
import com.postonmywall.common.MediaType;
import com.postonmywall.common.Platform;
import com.postonmywall.common.PublishStatus;
import com.postonmywall.file.MediaFile;
import com.postonmywall.file.MediaFileService;
import com.postonmywall.file.S3Service;
import com.postonmywall.publish.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublishServiceTest {

    @Mock private PublishLogRepository publishLogRepository;
    @Mock private MediaFileService mediaFileService;
    @Mock private SocialAccountService socialAccountService;
    @Mock private S3Service s3Service;
    @Mock private SocialAdapterRegistry adapterRegistry;
    @Mock private UserRepository userRepository;
    @Mock private SocialMediaAdapter adapter;

    @InjectMocks private PublishService publishService;

    private UUID userId;
    private User user;
    private MediaFile mediaFile;
    private SocialAccount socialAccount;
    private PublishRequest publishRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .username("mouad")
                .email("mouad@test.com")
                .password("encoded")
                .build();

        mediaFile = MediaFile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .s3Key("media/" + userId + "/test.mp4")
                .originalName("test.mp4")
                .mediaType(MediaType.VIDEO)
                .sizeBytes(1024L)
                .status(FileStatus.ACTIVE)
                .build();

        socialAccount = SocialAccount.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(Platform.TWITTER)
                .accountId("@mouad_toto")
                .accessToken("oauth-access-token")
                .active(true)
                .build();

        publishRequest = new PublishRequest();
        publishRequest.setMediaFileId(mediaFile.getId());
        publishRequest.setSocialAccountId(socialAccount.getId());
        publishRequest.setTitle("Test post title");
        publishRequest.setDescription("Test description");
    }

    @Test
    void publish_shouldReturnPublished_onSuccess() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaFileService.getActiveFile(userId, mediaFile.getId())).thenReturn(mediaFile);
        when(socialAccountService.getActiveAccount(userId, socialAccount.getId())).thenReturn(socialAccount);
        when(s3Service.generatePresignedUrl(any(), anyLong())).thenReturn("https://s3.example.com/file.mp4");
        when(adapterRegistry.get(Platform.TWITTER)).thenReturn(adapter);
        when(adapter.publish(any(), any(), any(), any(), any())).thenReturn("tweet-id-123");
        when(publishLogRepository.save(any(PublishLog.class))).thenAnswer(inv -> inv.getArgument(0));

        PublishResponse result = publishService.publish(userId, publishRequest);

        assertThat(result.getStatus()).isEqualTo(PublishStatus.PUBLISHED);
        assertThat(result.getExternalPostId()).isEqualTo("tweet-id-123");
        assertThat(result.getPlatform()).isEqualTo(Platform.TWITTER);
        verify(adapter).publish(
                eq("oauth-access-token"), isNull(),
                eq("https://s3.example.com/file.mp4"),
                eq("Test post title"), eq("Test description")
        );
    }

    @Test
    void publish_shouldReturnFailed_whenAdapterThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaFileService.getActiveFile(userId, mediaFile.getId())).thenReturn(mediaFile);
        when(socialAccountService.getActiveAccount(userId, socialAccount.getId())).thenReturn(socialAccount);
        when(s3Service.generatePresignedUrl(any(), anyLong())).thenReturn("https://s3.example.com/file.mp4");
        when(adapterRegistry.get(Platform.TWITTER)).thenReturn(adapter);
        when(adapter.publish(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Twitter rate limit exceeded"));
        when(publishLogRepository.save(any(PublishLog.class))).thenAnswer(inv -> inv.getArgument(0));

        PublishResponse result = publishService.publish(userId, publishRequest);

        assertThat(result.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("rate limit");
    }

    @Test
    void remove_shouldReturnRemoved_onSuccess() {
        UUID logId = UUID.randomUUID();
        PublishLog publishLog = PublishLog.builder()
                .id(logId)
                .user(user)
                .mediaFile(mediaFile)
                .socialAccount(socialAccount)
                .platform(Platform.TWITTER)
                .externalPostId("tweet-id-123")
                .status(PublishStatus.PUBLISHED)
                .build();

        when(publishLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(publishLog));
        when(adapterRegistry.get(Platform.TWITTER)).thenReturn(adapter);
        doNothing().when(adapter).remove(any(), any());
        when(publishLogRepository.save(any(PublishLog.class))).thenAnswer(inv -> inv.getArgument(0));

        PublishResponse result = publishService.remove(userId, logId);

        assertThat(result.getStatus()).isEqualTo(PublishStatus.REMOVED);
        assertThat(result.getRemovedAt()).isNotNull();
        verify(adapter).remove("oauth-access-token", "tweet-id-123");
    }
}
