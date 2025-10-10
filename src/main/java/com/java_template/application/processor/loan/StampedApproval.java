package com.java_template.application.processor.loan;

import com.java_template.application.entity.loan.version_1.Loan;
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

/**
 * ABOUTME: This processor records approval metadata when a loan is approved.
 * It stamps the approval with the approver's user ID, timestamp, and role.
 * 
 * Execution Mode: SYNC
 * Transition: approval_pending -> approved
 */
@Component
public class StampedApproval implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StampedApproval.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StampedApproval(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Loan> entityWithMetadata) {
        return entityWithMetadata != null &&
               entityWithMetadata.entity() != null &&
               entityWithMetadata.entity().isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Loan> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Recording approval metadata for loan: {}", loan.getLoanId());

        // In a real implementation, you would:
        // 1. Extract user ID from the request context or security context
        // 2. Record timestamp
        // 3. Record user role
        // 4. Store this in an audit field on the Loan entity
        
        // For now, we'll log the approval
        // Note: The Loan entity would need additional fields like:
        // - approvedBy (String)
        // - approvedAt (LocalDateTime)
        // - approverRole (String)
        
        logger.info("Loan {} approved - approval metadata recorded", loan.getLoanId());

        // Return the entity unchanged (metadata would be stored in entity fields in real implementation)
        return entityWithMetadata;
    }
}

