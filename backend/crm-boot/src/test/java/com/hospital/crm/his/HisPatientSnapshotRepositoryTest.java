package com.hospital.crm.his;

import com.hospital.crm.his.domain.HisPatientSnapshot;
import com.hospital.crm.his.domain.HisPatientSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证患者快照的 JSON 映射可读写（H2 PostgreSQL 模式），
 * 真实 PostgreSQL 的 jsonb 列由 Flyway V5 管理。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crm_snapshot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "crm.security.bootstrap-admin.enabled=false"
})
class HisPatientSnapshotRepositoryTest {

    @Autowired
    private HisPatientSnapshotRepository repository;

    @Test
    void persistsAndReadsJsonSnapshot() {
        Instant fetchedAt = Instant.parse("2026-11-16T08:00:00Z");
        HisPatientSnapshot snapshot = new HisPatientSnapshot();
        snapshot.setSourceSystem("HIS");
        snapshot.setExternalPatientId("P-TEST-001");
        snapshot.setSnapshot(Map.of(
                "externalPatientId", "P-TEST-001",
                "dataStatus", "LIVE",
                "sourceSystem", "HIS"));
        snapshot.setFetchedAt(fetchedAt);
        snapshot.setValidUntil(fetchedAt.plusSeconds(1800));
        snapshot.setMatchConfidence(new BigDecimal("0.9900"));
        repository.saveAndFlush(snapshot);

        HisPatientSnapshot loaded = repository
                .findBySourceSystemAndExternalPatientId("HIS", "P-TEST-001")
                .orElseThrow();

        assertEquals("P-TEST-001", loaded.getSnapshot().get("externalPatientId"));
        assertEquals("LIVE", loaded.getSnapshot().get("dataStatus"));
        assertEquals(0, new BigDecimal("0.9900").compareTo(loaded.getMatchConfidence()));

        repository.delete(loaded);
    }
}
