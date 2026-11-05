package com.example.plms.eligibility;

import com.example.plms.audit.AuditService;
import com.example.plms.common.ApiException;
import com.example.plms.core.eligibility.EligibilityEngine;
import com.example.plms.credit.CreditAssessment;
import com.example.plms.credit.CreditAssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EligibilityService {

    private final EligibilityPolicyRepository policies;
    private final EligibilityAssessmentRepository assessments;
    private final EligibilityRuleResultRepository ruleResults;
    private final CreditAssessmentService credit;
    private final AuditService audit;
    private final Clock clock;
    private final EligibilityEngine engine = new EligibilityEngine();

    public EligibilityService(EligibilityPolicyRepository policies, EligibilityAssessmentRepository assessments,
                              EligibilityRuleResultRepository ruleResults, CreditAssessmentService credit,
                              AuditService audit, Clock clock) {
        this.policies = policies;
        this.assessments = assessments;
        this.ruleResults = ruleResults;
        this.credit = credit;
        this.audit = audit;
        this.clock = clock;
    }

    public record Evaluation(EligibilityAssessment assessment, List<EligibilityRuleResult> rules,
                             boolean autoRejectOnFail) {
        public EligibilityEngine.Outcome outcome() {
            return EligibilityEngine.Outcome.valueOf(assessment.getOutcome());
        }
    }

    @Transactional(readOnly = true)
    public EligibilityPolicyEntity policy(String version) {
        return policies.findById(version).orElseThrow(() -> ApiException.conflict("POLICY_NOT_FOUND",
                "The eligibility policy version is not configured."));
    }

    /** Deterministic evaluation; persists the outcome, every rule result and the policy version used. */
    @Transactional
    public Evaluation evaluate(EligibilityDtos.EvaluationCommand c, String actorId) {
        EligibilityPolicyEntity policyEntity = policy(c.policyVersion());
        EligibilityEngine.Policy policy = policyEntity.toPolicy();

        EligibilityEngine.CreditStatus creditStatus = EligibilityEngine.CreditStatus.NOT_REQUESTED;
        Integer score = null;
        String creditId = null;
        if (policy.creditRequired()) {
            CreditAssessment ca = credit.assess(c.applicationId(), c.customerId(), actorId);
            creditId = ca.getId();
            if (ca.isAvailable()) {
                creditStatus = EligibilityEngine.CreditStatus.AVAILABLE;
                score = ca.getScore();
            } else {
                creditStatus = EligibilityEngine.CreditStatus.UNAVAILABLE;
            }
        }

        EligibilityEngine.Result result = engine.evaluate(policy, new EligibilityEngine.Input(
                c.requestedAmount(), c.requestedMonths(), c.minAmount(), c.maxAmount(), c.minMonths(), c.maxMonths(),
                c.annualRate(), c.monthlyIncome(), c.existingMonthlyObligations(), c.employmentType(),
                c.employmentMonths(), c.ageYears(), creditStatus, score));

        Instant now = Instant.now(clock);
        EligibilityAssessment assessment = assessments.save(new EligibilityAssessment(c.applicationId(),
                result.policyVersion(), result.outcome().name(), result.estimatedEmi(), result.debtToIncomeRatio(),
                creditId, actorId, now));
        List<EligibilityRuleResult> saved = new ArrayList<>();
        int seq = 0;
        for (EligibilityEngine.RuleResult r : result.rules()) {
            saved.add(ruleResults.save(new EligibilityRuleResult(assessment.getId(), ++seq, r.code(),
                    r.result().name(), r.reasonCode(), r.explanation())));
        }
        audit.record(actorId, "ELIGIBILITY_EVALUATED", "LOAN_APPLICATION", c.applicationId(), "SUCCESS",
                Map.of("outcome", result.outcome().name(), "policyVersion", result.policyVersion()));
        return new Evaluation(assessment, saved, policyEntity.isAutoRejectOnFail());
    }

    @Transactional(readOnly = true)
    public Optional<Evaluation> latest(String applicationId) {
        return assessments.findFirstByApplicationIdOrderByEvaluatedAtDesc(applicationId).map(this::load);
    }

    @Transactional(readOnly = true)
    public Evaluation byId(String assessmentId) {
        return assessments.findById(assessmentId).map(this::load)
                .orElseThrow(() -> ApiException.notFound("Assessment"));
    }

    @Transactional(readOnly = true)
    public List<EligibilityAssessment> history(String applicationId) {
        return assessments.findByApplicationIdOrderByEvaluatedAtDesc(applicationId);
    }

    @Transactional
    public EligibilityPolicyEntity createPolicy(String adminId, EligibilityDtos.PolicyRequest request) {
        if (policies.existsById(request.policyVersion())) {
            throw ApiException.conflict("POLICY_VERSION_EXISTS",
                    "Policy versions are immutable; create a new version instead.");
        }
        EligibilityPolicyEntity entity;
        try {
            entity = new EligibilityPolicyEntity(request, Instant.now(clock));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", e.getMessage());
        }
        entity = policies.save(entity);
        audit.record(adminId, "ELIGIBILITY_POLICY_CREATED", "ELIGIBILITY_POLICY", entity.getPolicyVersion(),
                "SUCCESS", Map.of());
        return entity;
    }

    @Transactional(readOnly = true)
    public List<EligibilityPolicyEntity> listPolicies() {
        return policies.findAllByOrderByCreatedAtDesc();
    }

    private Evaluation load(EligibilityAssessment a) {
        boolean autoReject = policies.findById(a.getPolicyVersion())
                .map(EligibilityPolicyEntity::isAutoRejectOnFail).orElse(false);
        return new Evaluation(a, ruleResults.findByAssessmentIdOrderBySeqAsc(a.getId()), autoReject);
    }

    public static EligibilityDtos.AssessmentResponse toResponse(Evaluation e) {
        EligibilityAssessment a = e.assessment();
        return new EligibilityDtos.AssessmentResponse(a.getId(), a.getApplicationId(), a.getOutcome(),
                a.getPolicyVersion(), a.getEstimatedEmi(), a.getDtiRatio(), views(e.rules()), a.getEvaluatedAt());
    }

    public static EligibilityDtos.CustomerAssessmentView toCustomerView(Evaluation e) {
        List<EligibilityDtos.RuleView> rules = views(e.rules());
        return new EligibilityDtos.CustomerAssessmentView(e.assessment().getId(), e.assessment().getOutcome(),
                e.assessment().getPolicyVersion(), count(rules, "PASS"), count(rules, "REVIEW"),
                count(rules, "PENDING"), count(rules, "FAIL"), rules, e.assessment().getEvaluatedAt());
    }

    private static List<EligibilityDtos.RuleView> views(List<EligibilityRuleResult> rules) {
        return rules.stream().map(r -> new EligibilityDtos.RuleView(r.getRuleCode(), r.getResult(),
                r.getReasonCode(), r.getExplanation())).toList();
    }

    private static long count(List<EligibilityDtos.RuleView> rules, String result) {
        return rules.stream().filter(r -> r.result().equals(result)).count();
    }
}
