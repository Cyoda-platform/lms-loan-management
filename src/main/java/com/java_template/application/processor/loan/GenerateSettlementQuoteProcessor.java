package com.java_template.application.processor.loan;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processor to generate a settlement quote for an active loan.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Creates a new SettlementQuote entity for the loan</li>
 *   <li>Sets initial quote data (quoteId, loanId, dates, requestedBy)</li>
 *   <li>Initializes totalAmountDue to zero (will be calculated by CalculateSettlementAmount processor)</li>
 *   <li>Sets expiration date to 7 days from creation</li>
 *   <li>Records creation audit information</li>
 * </ul>
 *
 * <p>Execution Mode: SYNC</p>
 * <p>Transition: active -> active (stays in active state)</p>
 *
 * <p>CRITICAL LIMITATIONS:</p>
 * <ul>
 *   <li>✅ ALLOWED: Read current loan data</li>
 *   <li>✅ ALLOWED: Create new SettlementQuote entity via EntityService</li>
 *   <li>❌ FORBIDDEN: Update current loan entity (processor cannot update the entity it's processing)</li>
 * </ul>
 */
@Component
public class GenerateSettlementQuoteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateSettlementQuoteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Default quote expiration period in days
    private static final int DEFAULT_QUOTE_EXPIRATION_DAYS = 7;

    public GenerateSettlementQuoteProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::generateSettlementQuote)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Loan> entityWithMetadata) {
        Loan loan = entityWithMetadata.entity();
        return loan != null && loan.isValid(entityWithMetadata.metadata()) && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to generate a settlement quote.
     *
     * <p>Creates a new SettlementQuote entity with initial data.
     * The quote will transition through its own workflow to calculate the settlement amount.</p>
     */
    private EntityWithMetadata<Loan> generateSettlementQuote(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Generating settlement quote for loan: {}", loan.getLoanId());

        // Validate loan is in appropriate state for settlement quote
        if (loan.getOutstandingPrincipal() == null || loan.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Cannot generate settlement quote for loan {} - no outstanding principal", loan.getLoanId());
            throw new IllegalStateException("Cannot generate settlement quote - loan has no outstanding principal");
        }

        // Create settlement date (default to today if not provided in request)
        // TODO: Extract settlement date from request parameters when controller is updated
        LocalDate settlementDate = LocalDate.now().plusDays(1); // Default to tomorrow
        LocalDate expirationDate = LocalDate.now().plusDays(DEFAULT_QUOTE_EXPIRATION_DAYS);

        // Create new settlement quote
        SettlementQuote quote = new SettlementQuote();
        quote.setQuoteId(UUID.randomUUID().toString());
        quote.setLoanId(loan.getLoanId());
        quote.setSettlementDate(settlementDate);
        quote.setExpirationDate(expirationDate);
        quote.setRequestedBy("SYSTEM"); // TODO: Extract from security context when available
        quote.setCurrency(loan.getCurrency());

        // Initialize totalAmountDue to zero - will be calculated by CalculateSettlementAmount processor
        quote.setTotalAmountDue(BigDecimal.ZERO);

        // Initialize audit information
        SettlementQuote.SettlementAudit audit = new SettlementQuote.SettlementAudit();
        audit.setCreatedAt(LocalDateTime.now());
        audit.setCreatedBy("SYSTEM"); // TODO: Extract from security context when available
        quote.setAudit(audit);

        // Create the settlement quote entity
        try {
            EntityWithMetadata<SettlementQuote> createdQuote = entityService.create(quote);

            logger.info("Settlement quote {} created successfully for loan {} with technical ID: {}",
                    quote.getQuoteId(),
                    loan.getLoanId(),
                    createdQuote.metadata().getId());

        } catch (Exception e) {
            logger.error("Error creating settlement quote for loan {}: {}",
                    loan.getLoanId(), e.getMessage());
            throw new IllegalStateException("Failed to create settlement quote", e);
        }

        // Return the loan entity unchanged
        // The loan remains in its current state
        return entityWithMetadata;
    }
}

