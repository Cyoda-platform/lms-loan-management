package com.java_template.application.processor.settlement_quote;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Processor to calculate the settlement amount for a settlement quote.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Retrieves the associated loan entity</li>
 *   <li>Calculates outstanding principal from loan</li>
 *   <li>Calculates accrued interest to current date from loan</li>
 *   <li>Projects interest from current date to settlement date</li>
 *   <li>Calculates total amount due (principal + total interest)</li>
 *   <li>Populates the calculation breakdown</li>
 *   <li>Records quotation timestamp in audit trail</li>
 * </ul>
 *
 * <p>Formula:</p>
 * <pre>
 * totalAmountDue = outstandingPrincipal + accruedInterestToDate + projectedInterestToSettlement
 * projectedInterestToSettlement = outstandingPrincipal × APR × (days / dayCountBasis)
 * </pre>
 *
 * <p>Execution Mode: ASYNC_NEW_TX</p>
 * <p>Transition: calculating -> quoted</p>
 */
@Component
public class CalculateSettlementAmount implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CalculateSettlementAmount.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Precision for monetary calculations
    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    // Day count basis for interest calculation (ACT/365)
    private static final BigDecimal DAY_COUNT_BASIS_365 = new BigDecimal("365");
    private static final BigDecimal DAY_COUNT_BASIS_360 = new BigDecimal("360");

    public CalculateSettlementAmount(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SettlementQuote.class)
                .validate(this::isValidEntityWithMetadata, "Invalid settlement quote entity wrapper")
                .map(this::calculateSettlement)
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
        return quote != null && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to calculate settlement amount.
     */
    private EntityWithMetadata<SettlementQuote> calculateSettlement(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SettlementQuote> context) {

        EntityWithMetadata<SettlementQuote> entityWithMetadata = context.entityResponse();
        SettlementQuote quote = entityWithMetadata.entity();

        logger.debug("Calculating settlement amount for quote: {}", quote.getQuoteId());

        // Retrieve the loan entity
        Loan loan = retrieveLoan(quote.getLoanId(), quote.getQuoteId());

        // Get current values from loan
        BigDecimal outstandingPrincipal = loan.getOutstandingPrincipal();
        BigDecimal accruedInterestToDate = loan.getAccruedInterest();
        BigDecimal apr = loan.getApr();
        String currency = loan.getCurrency();

        // Validate required fields
        if (outstandingPrincipal == null || outstandingPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Outstanding principal is null or zero for loan {} (quote: {})",
                    quote.getLoanId(), quote.getQuoteId());
            throw new IllegalStateException("Outstanding principal is required for settlement calculation");
        }

        if (apr == null) {
            logger.error("APR is null for loan {} (quote: {})", quote.getLoanId(), quote.getQuoteId());
            throw new IllegalStateException("APR is required for settlement calculation");
        }

        // Calculate projected interest from today to settlement date
        LocalDate today = LocalDate.now();
        LocalDate settlementDate = quote.getSettlementDate();
        
        BigDecimal projectedInterest = BigDecimal.ZERO;
        if (settlementDate.isAfter(today)) {
            long daysToSettlement = ChronoUnit.DAYS.between(today, settlementDate);
            
            // Use ACT/365 day count convention (default)
            // TODO: Get day count convention from loan when available
            BigDecimal dayCountBasis = DAY_COUNT_BASIS_365;
            BigDecimal dayCountFraction = new BigDecimal(daysToSettlement)
                    .divide(dayCountBasis, 10, RoundingMode.HALF_UP);
            
            projectedInterest = outstandingPrincipal
                    .multiply(apr)
                    .multiply(dayCountFraction)
                    .setScale(MONETARY_SCALE, MONETARY_ROUNDING);
            
            logger.debug("Projected interest for {} days: {} (principal: {}, APR: {}, fraction: {})",
                    daysToSettlement, projectedInterest, outstandingPrincipal, apr, dayCountFraction);
        }

        // Calculate total interest
        BigDecimal totalInterest = (accruedInterestToDate != null ? accruedInterestToDate : BigDecimal.ZERO)
                .add(projectedInterest);

        // Calculate total amount due
        BigDecimal totalAmountDue = outstandingPrincipal
                .add(totalInterest)
                .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

        // Populate calculation breakdown
        SettlementQuote.SettlementCalculation calculation = new SettlementQuote.SettlementCalculation();
        calculation.setOutstandingPrincipal(outstandingPrincipal);
        calculation.setAccruedInterestToDate(accruedInterestToDate != null ? accruedInterestToDate : BigDecimal.ZERO);
        calculation.setProjectedInterestToSettlement(projectedInterest);
        calculation.setFees(BigDecimal.ZERO); // No fees in MVP
        calculation.setBreakCosts(BigDecimal.ZERO); // No break costs in MVP
        calculation.setTotalInterest(totalInterest);
        calculation.setCalculationMethod("ACT/365");
        calculation.setDayCountBasis("365");

        quote.setCalculation(calculation);
        quote.setTotalAmountDue(totalAmountDue);
        quote.setCurrency(currency);

        // Update audit information
        if (quote.getAudit() == null) {
            quote.setAudit(new SettlementQuote.SettlementAudit());
        }
        quote.getAudit().setQuotedAt(LocalDateTime.now());
        quote.getAudit().setQuotedBy("SYSTEM"); // TODO: Extract from security context when available

        logger.info("Settlement amount calculated for quote {}: {} {} (principal: {}, accrued: {}, projected: {})",
                quote.getQuoteId(), totalAmountDue, currency,
                outstandingPrincipal, accruedInterestToDate, projectedInterest);

        return entityWithMetadata;
    }

    /**
     * Retrieves the loan entity by business ID.
     */
    private Loan retrieveLoan(String loanId, String quoteId) {
        ModelSpec loanModelSpec = new ModelSpec()
                .withName(Loan.ENTITY_NAME)
                .withVersion(Loan.ENTITY_VERSION);

        EntityWithMetadata<Loan> loanWithMetadata;
        try {
            loanWithMetadata = entityService.findByBusinessId(
                    loanModelSpec,
                    loanId,
                    "loanId",
                    Loan.class
            );
        } catch (Exception e) {
            logger.error("Error retrieving loan {} for quote {}: {}",
                    loanId, quoteId, e.getMessage());
            throw new IllegalStateException("Failed to retrieve loan: " + loanId, e);
        }

        if (loanWithMetadata == null) {
            logger.error("Loan {} not found for quote: {}", loanId, quoteId);
            throw new IllegalStateException("Loan not found: " + loanId);
        }

        return loanWithMetadata.entity();
    }
}

