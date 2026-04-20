package com.postonmywall.account;

import com.postonmywall.auth.User;
import com.postonmywall.auth.UserRepository;
import com.postonmywall.exception.ResourceAlreadyExistsException;
import com.postonmywall.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                .collect(Collectors.toList());
    }

    public void unlinkAccount(UUID userId, UUID accountId) {
        SocialAccount account = socialAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Social account not found"));
        account.setActive(false);
        socialAccountRepository.save(account);
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
