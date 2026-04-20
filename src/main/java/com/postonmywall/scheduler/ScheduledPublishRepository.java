package com.postonmywall.scheduler;

import com.postonmywall.common.Frequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledPublishRepository extends JpaRepository<ScheduledPublish, UUID> {
    List<ScheduledPublish> findAllByUserId(UUID userId);
    Optional<ScheduledPublish> findByIdAndUserId(UUID id, UUID userId);
    List<ScheduledPublish> findAllByActiveTrueAndFrequency(Frequency frequency);
}
