package com.hospital.crm.callcenter.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SmsTemplateRepository
        extends JpaRepository<SmsTemplate, UUID>, JpaSpecificationExecutor<SmsTemplate> {

    Optional<SmsTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
