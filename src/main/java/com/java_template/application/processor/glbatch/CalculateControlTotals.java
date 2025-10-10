package com.java_template.application.processor.glbatch;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * ABOUTME: This processor calculates control totals for the GL batch.
 * 
 * Execution Mode: SYNC
 * Transition: open -> prepared
 * 
 * Logic:
 * 1. Sum all debit entries
 * 2. Sum all credit entries
 * 3. Count total lines
 * 4. Check if balanced (debits == credits)
 */
@Component
public class CalculateControlTotals implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CalculateControlTotals.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CalculateControlTotals(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(GLBatch.class)
                .validate(this::isValidEntityWithMetadata, "Invalid GLBatch entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<GLBatch> entityWithMetadata) {
        return entityWithMetadata != null &&
               entityWithMetadata.entity() != null &&
               entityWithMetadata.entity().isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<GLBatch> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<GLBatch> context) {

        EntityWithMetadata<GLBatch> entityWithMetadata = context.entityResponse();
        GLBatch batch = entityWithMetadata.entity();

        logger.debug("Calculating control totals for GLBatch: {}", batch.getBatchId());

        List<GLBatch.GLLine> glLines = batch.getGlLines();
        
        if (glLines == null || glLines.isEmpty()) {
            logger.warn("GLBatch {} has no GL lines", batch.getBatchId());
            // Initialize empty control totals
            GLBatch.ControlTotals controlTotals = new GLBatch.ControlTotals();
            controlTotals.setTotalDebits(BigDecimal.ZERO);
            controlTotals.setTotalCredits(BigDecimal.ZERO);
            controlTotals.setLineCount(0);
            controlTotals.setIsBalanced(true);
            batch.setControlTotals(controlTotals);
            return entityWithMetadata;
        }

        // Calculate totals
        BigDecimal totalDebits = glLines.stream()
            .filter(line -> "DEBIT".equalsIgnoreCase(line.getType()))
            .map(GLBatch.GLLine::getAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = glLines.stream()
            .filter(line -> "CREDIT".equalsIgnoreCase(line.getType()))
            .map(GLBatch.GLLine::getAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int lineCount = glLines.size();
        boolean isBalanced = totalDebits.compareTo(totalCredits) == 0;

        // Create control totals
        GLBatch.ControlTotals controlTotals = new GLBatch.ControlTotals();
        controlTotals.setTotalDebits(totalDebits);
        controlTotals.setTotalCredits(totalCredits);
        controlTotals.setLineCount(lineCount);
        controlTotals.setIsBalanced(isBalanced);

        batch.setControlTotals(controlTotals);

        logger.info("Control totals calculated for batch {}: Debits={}, Credits={}, Lines={}, Balanced={}", 
                   batch.getBatchId(), totalDebits, totalCredits, lineCount, isBalanced);

        if (!isBalanced) {
            logger.warn("GLBatch {} is NOT balanced! Difference: {}", 
                       batch.getBatchId(), 
                       totalDebits.subtract(totalCredits));
        }

        return entityWithMetadata;
    }
}

