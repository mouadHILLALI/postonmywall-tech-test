package com.postonmywall.file;

import com.postonmywall.common.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    Page<MediaFile> findAllByUserIdAndStatus(UUID userId, FileStatus status, Pageable pageable);

    Optional<MediaFile> findByIdAndUserId(UUID id, UUID userId);
}
