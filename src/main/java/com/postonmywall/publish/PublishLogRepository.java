package com.postonmywall.publish;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PublishLogRepository extends JpaRepository<PublishLog, UUID> {
    Page<PublishLog> findAllByUserId(UUID userId, Pageable pageable);
    Optional<PublishLog> findByIdAndUserId(UUID id, UUID userId);
}
