package com.booking.event.repository;

import com.booking.event.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord,Long> {

    Optional<IdempotencyRecord> findByShowIdAndUserIdAndIdempotencyKey(
            Long showId,
            Long userId,
            String idempotencyKey
    );

}
