package com.java_template.application.criterion.loan;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ABOUTME: This criterion checks if a loan has reached maturity and is fully paid.
 * 
 * A loan can be closed when:
 * 1. The maturity date has been reached (maturityDate <= today)
 * 2. The outstanding principal is zero (fully paid)
 * 
 * This is a pure function with no side effects.
 * Used for automatic transition: active -> closed
 */
@Component
public class LoanMaturityAndFullyPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public LoanMaturityAndFullyPaidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking loan maturity and fully paid criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::checkMaturityAndFullyPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Evaluates whether the loan has reached maturity and is fully paid.
     * 
     * @param context The criterion evaluation context containing the loan
     * @return EvaluationOutcome.success() if both conditions are met, otherwise failure with reason
     */
    public EvaluationOutcome checkMaturityAndFullyPaid(
            CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        
        Loan loan = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (loan == null) {
            logger.warn("Loan entity is null");
            return EvaluationOutcome.fail("Loan entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check maturity date
        if (loan.getMaturityDate() == null) {
            logger.warn("Loan {} has no maturity date", loan.getLoanId());
            return EvaluationOutcome.fail(
                "Loan has no maturity date",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        LocalDate today = LocalDate.now();
        boolean maturityReached = !loan.getMaturityDate().isAfter(today);

        if (!maturityReached) {
            logger.debug("Loan {} maturity date {} has not been reached yet (today: {})", 
                        loan.getLoanId(), loan.getMaturityDate(), today);
            return EvaluationOutcome.fail(
                String.format("Maturity date %s has not been reached yet", loan.getMaturityDate()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check outstanding principal
        if (loan.getOutstandingPrincipal() == null) {
            logger.warn("Loan {} has no outstanding principal value", loan.getLoanId());
            return EvaluationOutcome.fail(
                "Loan has no outstanding principal value",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        boolean fullyPaid = loan.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) == 0;

        if (!fullyPaid) {
            logger.debug("Loan {} still has outstanding principal: {}", 
                        loan.getLoanId(), loan.getOutstandingPrincipal());
            return EvaluationOutcome.fail(
                String.format("Loan still has outstanding principal: %s", loan.getOutstandingPrincipal()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Both conditions met
        logger.debug("Loan {} has reached maturity and is fully paid - ready for closure", loan.getLoanId());
        return EvaluationOutcome.success();
    }
}

