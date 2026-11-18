package com.hospital.crm.callcenter.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CallRecordingRepository extends JpaRepository<CallRecording, UUID> {

    /** 同一厂商录音键复用同一条记录，避免重复入库。 */
    Optional<CallRecording> findByRecordingKey(String recordingKey);

    /** 录音访问计数，数据库端自增避免并发覆盖。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CallRecording r set r.accessCount = r.accessCount + 1 where r.id = :id")
    int incrementAccess(@Param("id") UUID id);
}
