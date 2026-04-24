package com.postonmywall.account;

import com.postonmywall.auth.User;
import com.postonmywall.auth.UserRepository;
import com.postonmywall.common.Platform;
import com.postonmywall.exception.ResourceAlreadyExistsException;
import com.postonmywall.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    public SocialAccountService(SocialAccountRepository socialAccountRepository,
                                UserRepository userRepository) {
        this.socialAccountRepository = socialAccountRepository;
        this.userRepository = userRepository;
    }

    public SocialAccountResponse linkAccount(UUID userId, LinkAccountRequest request) {
        if (socialAccountRepository.existsByUserIdAndPlatformAndAccountId(
                userId, request.getPlatform(), request.getAccountId())) {
            throw new ResourceAlreadyExistsException(
                    "Account '" + request.getAccountId() + "' on " + request.getPlatform() + " is already linked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SocialAccount account = SocialAccount.builder()
                .user(user)
                .platform(request.getPlatform())
                .accountId(request.getAccountId())
                .accessToken(request.getAccessToken())
                .tokenSecret(request.getTokenSecret())
                .active(true)
                .build();

        return toResponse(socialAccountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<SocialAccountResponse> listAccounts(UUID userId) {
        return socialAccountRepository.findAllByUserIdAndActiveTrue(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void unlinkAccount(UUID userId, UUID accountId) {
        SocialAccount account = socialAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Social account not found"));
        account.setActive(false);
        socialAccountRepository.save(account);
    }

    public void updateAccessToken(UUID accountId, String newAccessToken) {
        SocialAccount account = socialAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Social account not found: " + accountId));
        account.setAccessToken(newAccessToken);
        socialAccountRepository.save(account);
    }

    public SocialAccountResponse upsertOAuthAccount(UUID userId, Platform platform,
                                                    String accountId, String accessToken,
                                                    String refreshToken, Instant tokenExpiresAt) {
        SocialAccount account = socialAccountRepository
                .findByUserIdAndPlatformAndAccountId(userId, platform, accountId)
                .orElse(null);

        if (account == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            account = SocialAccount.builder()
                    .user(user)
                    .platform(platform)
                    .accountId(accountId)
                    .active(true)
                    .build();
        } else {
            account.setActive(true);
        }

        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiresAt(tokenExpiresAt);

        return toResponse(socialAccountRepository.save(account));
    }

    // Used internally by PublishService and SchedulerService
    public SocialAccount getActiveAccount(UUID userId, UUID accountId) {
        return socialAccountRepository.findByIdAndUserIdAndActiveTrue(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Active social account not found: " + accountId));
    }

    private SocialAccountResponse toResponse(SocialAccount account) {
        return new SocialAccountResponse(
                account.getId(),
                account.getPlatform(),
                account.getAccountId(),
                account.isActive(),
                account.getCreatedAt()
        );
    }
}
