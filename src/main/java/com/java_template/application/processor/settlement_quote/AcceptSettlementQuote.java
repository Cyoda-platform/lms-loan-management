package com.java_template.application.processor.settlement_quote;

import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
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

import java.time.LocalDateTime;

/**
 * Processor to accept a settlement quote.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Records the acceptance timestamp in the audit trail</li>
 *   <li>Records the user who accepted the quote</li>
 *   <li>Validates that the quote has not expired</li>
 * </ul>
 *
 * <p>Execution Mode: SYNC</p>
 * <p>Transition: quoted -> accepted</p>
 *
 * <p>After acceptance, the loan's SettlementQuoteAcceptedAndPaidCriterion will trigger
 * the ApplySettlement processor to close the loan when payment is received.</p>
 */
@Component
public class AcceptSettlementQuote implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AcceptSettlementQuote.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AcceptSettlementQuote(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SettlementQuote.class)
                .validate(this::isValidEntityWithMetadata, "Invalid settlement quote entity wrapper")
                .map(this::acceptQuote)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<SettlementQuote> entityWithMetadata) {
        SettlementQuote quote = entityWithMetadata.entity();
        return quote != null && quote.isValid(entityWithMetadata.metadata()) && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to accept the settlement quote.
     */
    private EntityWithMetadata<SettlementQuote> acceptQuote(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SettlementQuote> context) {

        EntityWithMetadata<SettlementQuote> entityWithMetadata = context.entityResponse();
        SettlementQuote quote = entityWithMetadata.entity();

        logger.debug("Accepting settlement quote: {}", quote.getQuoteId());

        // Validate quote has not expired
        if (quote.getExpirationDate() != null && quote.getExpirationDate().isBefore(java.time.LocalDate.now())) {
            logger.error("Cannot accept expired quote {} (expired: {})",
                    quote.getQuoteId(), quote.getExpirationDate());
            throw new IllegalStateException("Cannot accept expired settlement quote");
        }

        // Validate quote has been calculated
        if (quote.getTotalAmountDue() == null || quote.getTotalAmountDue().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            logger.error("Cannot accept quote {} - total amount due not calculated",
                    quote.getQuoteId());
            throw new IllegalStateException("Cannot accept quote - total amount due not calculated");
        }

        // Update audit information
        if (quote.getAudit() == null) {
            quote.setAudit(new SettlementQuote.SettlementAudit());
        }
        quote.getAudit().setAcceptedAt(LocalDateTime.now());
        quote.getAudit().setAcceptedBy("SYSTEM"); // TODO: Extract from security context when available

        logger.info("Settlement quote {} accepted for loan {} - total amount due: {} {}",
                quote.getQuoteId(),
                quote.getLoanId(),
                quote.getTotalAmountDue(),
                quote.getCurrency());

        return entityWithMetadata;
    }
}

