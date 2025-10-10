package com.java_template.application.criterion.glbatch;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
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

/**
 * ABOUTME: This criterion checks if the GL batch control totals are balanced.
 * 
 * A batch is balanced when:
 * - Total debits equal total credits
 * - Control totals are present and valid
 * 
 * This is a pure function with no side effects.
 * Used for transition: prepared -> maker_approved
 */
@Component
public class ControlTotalsBalanced implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ControlTotalsBalanced(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking control totals balanced criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(GLBatch.class, this::checkControlTotalsBalanced)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    public EvaluationOutcome checkControlTotalsBalanced(
            CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context) {
        
        GLBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("GLBatch entity is null");
            return EvaluationOutcome.fail("GLBatch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if control totals exist
        if (batch.getControlTotals() == null) {
            logger.warn("GLBatch {} has no control totals", batch.getBatchId());
            return EvaluationOutcome.fail(
                "Control totals are missing",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        GLBatch.ControlTotals controlTotals = batch.getControlTotals();

        // Check if totals are present
        if (controlTotals.getTotalDebits() == null || controlTotals.getTotalCredits() == null) {
            logger.warn("GLBatch {} has incomplete control totals", batch.getBatchId());
            return EvaluationOutcome.fail(
                "Total debits or credits are missing",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        // Check if debits equal credits
        boolean isBalanced = controlTotals.getTotalDebits().compareTo(controlTotals.getTotalCredits()) == 0;

        if (!isBalanced) {
            logger.warn("GLBatch {} is not balanced - Debits: {}, Credits: {}", 
                       batch.getBatchId(), 
                       controlTotals.getTotalDebits(), 
                       controlTotals.getTotalCredits());
            return EvaluationOutcome.fail(
                String.format("Batch is not balanced - Debits: %s, Credits: %s", 
                             controlTotals.getTotalDebits(), 
                             controlTotals.getTotalCredits()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("GLBatch {} control totals are balanced: {}", 
                    batch.getBatchId(), 
                    controlTotals.getTotalDebits());
        return EvaluationOutcome.success();
    }
}

