package com.java_template.application.criterion.loan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ABOUTME: This criterion checks if a settlement quote has been accepted and paid.
 * 
 * A loan can be settled when:
 * 1. An accepted SettlementQuote exists for this loan
 * 2. A payment has been received matching the settlement amount
 * 
 * This is a pure function with no side effects.
 * Used for automatic transition: active -> settled
 */
@Component
public class SettlementQuoteAcceptedAndPaidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SettlementQuoteAcceptedAndPaidCriterion(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking settlement quote accepted and paid criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::checkSettlementQuoteAcceptedAndPaid)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Evaluates whether an accepted settlement quote exists and payment has been received.
     * 
     * @param context The criterion evaluation context containing the loan
     * @return EvaluationOutcome.success() if both conditions are met, otherwise failure with reason
     */
    public EvaluationOutcome checkSettlementQuoteAcceptedAndPaid(
            CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        
        Loan loan = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (loan == null) {
            logger.warn("Loan entity is null");
            return EvaluationOutcome.fail("Loan entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String loanId = loan.getLoanId();
        if (loanId == null || loanId.trim().isEmpty()) {
            logger.warn("Loan has no loanId");
            return EvaluationOutcome.fail(
                "Loan has no loanId",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        // Find accepted settlement quote for this loan
        SettlementQuote acceptedQuote = findAcceptedSettlementQuote(loanId);
        
        if (acceptedQuote == null) {
            logger.debug("No accepted settlement quote found for loan {}", loanId);
            return EvaluationOutcome.fail(
                "No accepted settlement quote found for this loan",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check if payment has been received for the settlement amount
        boolean paymentReceived = checkSettlementPaymentReceived(loanId, acceptedQuote.getTotalAmountDue());
        
        if (!paymentReceived) {
            logger.debug("Settlement payment not yet received for loan {} (expected: {})", 
                        loanId, acceptedQuote.getTotalAmountDue());
            return EvaluationOutcome.fail(
                String.format("Settlement payment not yet received (expected: %s)", acceptedQuote.getTotalAmountDue()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Both conditions met
        logger.debug("Loan {} has accepted settlement quote and payment received - ready for settlement", loanId);
        return EvaluationOutcome.success();
    }

    /**
     * Finds an accepted settlement quote for the given loan.
     * 
     * @param loanId The loan ID to search for
     * @return The accepted SettlementQuote, or null if none found
     */
    private SettlementQuote findAcceptedSettlementQuote(String loanId) {
        try {
            ModelSpec quoteModelSpec = new ModelSpec()
                .withName(SettlementQuote.ENTITY_NAME)
                .withVersion(SettlementQuote.ENTITY_VERSION);

            // Query all settlement quotes
            List<EntityWithMetadata<SettlementQuote>> quotes = 
                entityService.findAll(quoteModelSpec, SettlementQuote.class);

            // Find accepted quote for this loan
            return quotes.stream()
                .map(EntityWithMetadata::entity)
                .filter(quote -> loanId.equals(quote.getLoanId()))
                .filter(quote -> isAcceptedState(quote))
                .findFirst()
                .orElse(null);

        } catch (Exception e) {
            logger.error("Error querying settlement quotes for loan {}: {}", loanId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a settlement quote is in accepted state.
     * This is a simplified check - in reality, you'd check the entity's lifecycle state.
     * 
     * @param quote The settlement quote to check
     * @return true if the quote is accepted
     */
    private boolean isAcceptedState(SettlementQuote quote) {
        // In a real implementation, you would check the entity's lifecycle state
        // For now, we'll check if the audit information shows it was accepted
        return quote.getAudit() != null && quote.getAudit().getAcceptedAt() != null;
    }

    /**
     * Checks if a payment has been received matching the settlement amount.
     * 
     * @param loanId The loan ID
     * @param settlementAmount The expected settlement amount
     * @return true if matching payment found
     */
    private boolean checkSettlementPaymentReceived(String loanId, BigDecimal settlementAmount) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec()
                .withName(Payment.ENTITY_NAME)
                .withVersion(Payment.ENTITY_VERSION);

            // Query all payments
            List<EntityWithMetadata<Payment>> payments = 
                entityService.findAll(paymentModelSpec, Payment.class);

            // Check if any payment matches the settlement amount for this loan
            return payments.stream()
                .map(EntityWithMetadata::entity)
                .filter(payment -> loanId.equals(payment.getLoanId()))
                .anyMatch(payment -> payment.getPaymentAmount() != null && 
                                    payment.getPaymentAmount().compareTo(settlementAmount) >= 0);

        } catch (Exception e) {
            logger.error("Error querying payments for loan {}: {}", loanId, e.getMessage(), e);
            return false;
        }
    }
}

