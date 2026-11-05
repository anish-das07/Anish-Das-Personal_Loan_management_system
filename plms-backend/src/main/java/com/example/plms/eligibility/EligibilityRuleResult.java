package com.example.plms.eligibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "eligibility_rule_result", indexes = @Index(name = "idx_rule_assessment", columnList = "assessment_id"))
public class EligibilityRuleResult {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "assessment_id", nullable = false, length = 36)
    private String assessmentId;
    @Column(nullable = false)
    private int seq;
    @Column(name = "rule_code", nullable = false, length = 40)
    private String ruleCode;
    @Column(nullable = false, length = 10)
    private String result;
    @Column(name = "reason_code", nullable = false, length = 60)
    private String reasonCode;
    @Column(nullable = false, length = 200)
    private String explanation;

    protected EligibilityRuleResult() {
    }

    public EligibilityRuleResult(String assessmentId, int seq, String ruleCode, String result, String reasonCode,
                                 String explanation) {
        this.id = UUID.randomUUID().toString();
        this.assessmentId = assessmentId;
        this.seq = seq;
        this.ruleCode = ruleCode;
        this.result = result;
        this.reasonCode = reasonCode;
        this.explanation = explanation;
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public int getSeq() {
        return seq;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getResult() {
        return result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getExplanation() {
        return explanation;
    }
}
