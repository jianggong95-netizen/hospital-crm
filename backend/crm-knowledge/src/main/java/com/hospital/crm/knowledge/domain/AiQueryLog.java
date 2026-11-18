package com.hospital.crm.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 问答追溯日志：问题、答案、置信度与引用来源，支撑答案可追溯与低置信度转人工。
 */
@Entity
@Table(name = "crm_ai_query_log")
public class AiQueryLog {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> refs = new ArrayList<>();

    @Column(nullable = false, length = 16)
    private String scene = "chat";

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "needs_handoff", nullable = false)
    private boolean needsHandoff;

    @Column(name = "asked_by")
    private UUID askedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (refs == null) {
            refs = new ArrayList<>();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public List<String> getRefs() {
        return refs;
    }

    public void setRefs(List<String> refs) {
        this.refs = refs;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isNeedsHandoff() {
        return needsHandoff;
    }

    public void setNeedsHandoff(boolean needsHandoff) {
        this.needsHandoff = needsHandoff;
    }

    public UUID getAskedBy() {
        return askedBy;
    }

    public void setAskedBy(UUID askedBy) {
        this.askedBy = askedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
