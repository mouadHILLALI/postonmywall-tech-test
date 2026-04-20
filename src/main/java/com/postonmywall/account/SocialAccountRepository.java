package com.postonmywall.account;

import com.postonmywall.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    List<SocialAccount> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<SocialAccount> findByIdAndUserId(UUID id, UUID userId);

    Optional<SocialAccount> findByIdAndUserIdAndActiveTrue(UUID id, UUID userId);

    boolean existsByUserIdAndPlatformAndAccountId(UUID userId, Platform platform, String accountId);
}
