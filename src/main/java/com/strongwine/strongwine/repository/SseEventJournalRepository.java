package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.SseEventJournal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SseEventJournalRepository extends JpaRepository<SseEventJournal, Long> {
    List<SseEventJournal> findByChannelAndTargetIdAndIdGreaterThanOrderByIdAsc(
        String channel,
        Long targetId,
        Long id,
        Pageable pageable
    );

    List<SseEventJournal> findByChannelAndTargetIdIsNullAndIdGreaterThanOrderByIdAsc(
        String channel,
        Long id,
        Pageable pageable
    );

    List<SseEventJournal> findByChannelAndIdGreaterThanOrderByIdAsc(
        String channel,
        Long id,
        Pageable pageable
    );

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
