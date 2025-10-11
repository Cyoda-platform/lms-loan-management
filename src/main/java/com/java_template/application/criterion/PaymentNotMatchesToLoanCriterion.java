package com.java_template.application.criterion;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Negative criterion that succeeds when PaymentMatchesToLoanCriterion fails.
 * This criterion is used to route payments to the unmatched state when they cannot
 * be matched to an active loan, allowing the matching failure reason to be captured.
 */
@Component
public class PaymentNotMatchesToLoanCriterion extends PaymentMatchesToLoanCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentNotMatchesToLoanCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        super(serializerFactory, entityService);
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if payment does NOT match to loan for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validateEntityFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Inverts the validation logic - succeeds when parent validation fails
     */
    private EvaluationOutcome validateEntityFailed(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        // Call parent validation logic
        EvaluationOutcome parentOutcome = super.validateEntity(context);

        // Invert the result
        if (parentOutcome.isSuccess()) {
            // Parent validation succeeded (payment matches), so this negative criterion should fail
            return EvaluationOutcome.fail("Payment matches to loan - unmatched path not needed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            // Parent validation failed (payment doesn't match), so this negative criterion should succeed
            // Return success so the transition is taken, but the warnings are still attached
            logger.info("Payment does not match to loan - taking unmatched path");
            return EvaluationOutcome.success();
        }
    }
}

