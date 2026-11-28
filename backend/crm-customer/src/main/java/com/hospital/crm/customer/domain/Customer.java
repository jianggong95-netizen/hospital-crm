package com.hospital.crm.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "crm_customer")
public class Customer {

    @Id
    private UUID id;

    @Column(name = "customer_no", nullable = false, length = 32, unique = true)
    private String customerNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 16)
    private String gender = "unknown";

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_encrypted")
    private String phoneEncrypted;

    @Column(name = "phone_hash", length = 128)
    private String phoneHash;

    @Column(name = "phone_masked", length = 32)
    private String phoneMasked;

    @Column(name = "id_no_encrypted")
    private String idNoEncrypted;

    @Column(name = "id_no_hash", length = 128)
    private String idNoHash;

    @Column(name = "id_no_masked", length = 32)
    private String idNoMasked;

    @Column(name = "source_code", length = 32)
    private String sourceCode;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "merged_into")
    private UUID mergedInto;

    @Column(name = "his_synced_at")
    private Instant hisSyncedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "crm_customer_tag",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 50)
    private Set<Tag> tags = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getPhoneEncrypted() {
        return phoneEncrypted;
    }

    public void setPhoneEncrypted(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked) {
        this.phoneMasked = phoneMasked;
    }

    public String getIdNoEncrypted() {
        return idNoEncrypted;
    }

    public void setIdNoEncrypted(String idNoEncrypted) {
        this.idNoEncrypted = idNoEncrypted;
    }

    public String getIdNoHash() {
        return idNoHash;
    }

    public void setIdNoHash(String idNoHash) {
        this.idNoHash = idNoHash;
    }

    public String getIdNoMasked() {
        return idNoMasked;
    }

    public void setIdNoMasked(String idNoMasked) {
        this.idNoMasked = idNoMasked;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getMergedInto() {
        return mergedInto;
    }

    public void setMergedInto(UUID mergedInto) {
        this.mergedInto = mergedInto;
    }

    public Instant getHisSyncedAt() {
        return hisSyncedAt;
    }

    public void setHisSyncedAt(Instant hisSyncedAt) {
        this.hisSyncedAt = hisSyncedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }
}
