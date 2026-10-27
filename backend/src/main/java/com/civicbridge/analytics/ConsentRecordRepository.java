package com.civicbridge.analytics;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
    @Modifying
    @Query("delete from ConsentRecord c where c.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
