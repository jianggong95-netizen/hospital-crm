package com.hospital.crm.followup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 随访模板：问题清单与风险规则以 JSONB 保存，模板更新时版本号递增。
 * risk_rules 结构：{"highRisk":[{"questionCode":"condition","values":["加重"]}],
 * "mediumRisk":[...],"requiresHuman":true}。
 */
@Entity
@Table(name = "crm_followup_template")
public class FollowupTemplate {

    @Id
    private UUID id;

    @Column(name = "template_code", nullable = false, length = 40)
    private String templateCode;

    @Column(name = "category_code", nullable = false, length = 40)
    private String categoryCode;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(nullable = false, length = 24)
    private String channel = "phone";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> questions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_rules", nullable = false)
    private Map<String, Object> riskRules = new LinkedHashMap<>();

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public List<Map<String, Object>> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Map<String, Object>> questions) {
        this.questions = questions;
    }

    public Map<String, Object> getRiskRules() {
        return riskRules;
    }

    public void setRiskRules(Map<String, Object> riskRules) {
        this.riskRules = riskRules;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
