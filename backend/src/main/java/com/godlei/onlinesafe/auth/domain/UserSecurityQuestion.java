package com.godlei.onlinesafe.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_security_question")
public class UserSecurityQuestion {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 36, updatable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private SecurityQuestionType questionType;

    @Column(name = "question_code", length = 64)
    private String questionCode;

    @Column(name = "question_text", nullable = false, length = 200)
    private String questionText;

    @Column(name = "answer_hash", nullable = false, length = 255)
    private String answerHash;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserSecurityQuestion() {
    }

    private UserSecurityQuestion(
            String ownerId,
            SecurityQuestionType questionType,
            String questionCode,
            String questionText,
            String answerHash,
            int sortOrder
    ) {
        this.id = UUID.randomUUID().toString();
        this.ownerId = Objects.requireNonNull(ownerId);
        this.questionType = Objects.requireNonNull(questionType);
        this.questionCode = questionCode;
        this.questionText = Objects.requireNonNull(questionText);
        this.answerHash = Objects.requireNonNull(answerHash);
        this.sortOrder = sortOrder;
    }

    public static UserSecurityQuestion create(
            String ownerId,
            SecurityQuestionType questionType,
            String questionCode,
            String questionText,
            String answerHash,
            int sortOrder
    ) {
        return new UserSecurityQuestion(ownerId, questionType, questionCode, questionText, answerHash, sortOrder);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public SecurityQuestionType getQuestionType() {
        return questionType;
    }

    public String getQuestionCode() {
        return questionCode;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getAnswerHash() {
        return answerHash;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
