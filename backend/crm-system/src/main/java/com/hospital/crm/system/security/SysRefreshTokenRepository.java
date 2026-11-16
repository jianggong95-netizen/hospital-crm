package com.hospital.crm.system.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SysRefreshTokenRepository extends JpaRepository<SysRefreshToken, UUID> {

    Optional<SysRefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update SysRefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllActive(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from SysRefreshToken t where t.expiresAt < :cutoff or (t.revokedAt is not null and t.revokedAt < :cutoff)")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
