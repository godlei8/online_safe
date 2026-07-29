package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportCandidateBucket;
import com.godlei.onlinesafe.vaultimport.domain.ImportIssueCode;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class ImportCandidate {

    public enum DuplicateAction {
        SKIP,
        CREATE
    }

    private String id;
    private int rowIndex;
    private double confidence;
    private ImportCandidateBucket bucket;
    private List<ImportIssueCode> issues = new ArrayList<>();
    private DuplicateAction duplicateAction;
    private JsonNode payload;
    private String sourceHint;
    /** 用户确认可导入后忽略 AI/置信度等审查类问题 */
    private boolean forceReady;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public ImportCandidateBucket getBucket() {
        return bucket;
    }

    public void setBucket(ImportCandidateBucket bucket) {
        this.bucket = bucket;
    }

    public List<ImportIssueCode> getIssues() {
        return issues;
    }

    public void setIssues(List<ImportIssueCode> issues) {
        this.issues = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
    }

    public DuplicateAction getDuplicateAction() {
        return duplicateAction;
    }

    public void setDuplicateAction(DuplicateAction duplicateAction) {
        this.duplicateAction = duplicateAction;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getSourceHint() {
        return sourceHint;
    }

    public void setSourceHint(String sourceHint) {
        this.sourceHint = sourceHint;
    }

    public boolean isForceReady() {
        return forceReady;
    }

    public void setForceReady(boolean forceReady) {
        this.forceReady = forceReady;
    }
}
