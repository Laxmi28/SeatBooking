package com.booking.event.repository;

import com.booking.event.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord,Long> {

    Optional<IdempotencyRecord> findByShowIdAndUserIdAndIdempotencyKey(
            Long showId,
            String userId,
            String idempotencyKey
    );

}
