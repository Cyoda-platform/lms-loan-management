package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.DayCountConvention;
import com.java_template.application.entity.loan.version_1.Loan;
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
import java.time.temporal.ChronoUnit;

/**
 * Consolidated processor to compute day-count fraction and calculate accrual interest amount.
 *
 * This processor combines the functionality of:
 * - DeriveDayCountFractionProcessor: Computes day-count fraction per product convention
 * - CalculateAccrualAmountProcessor: Calculates interest amount using the formula
 *
 * Supports three day count conventions:
 * - ACT_360: Actual days / 360
 * - ACT_365: Actual days / 365
 * - THIRTY_360: 30/360 (assumes 30 days per month, 360 days per year)
 *
 * Formula: interestAmount = principal × APR × dayCountFraction
 *
 * This processor runs in SYNC mode and updates the accrual entity with both
 * the calculated fraction and the interest amount.
 */
@Component
public class CalculateAccrualProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CalculateAccrualProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Precision for monetary calculations (2 decimal places for most currencies)
    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    public CalculateAccrualProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CalculateAccrual for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::calculateDayCountFractionAndInterest)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CalculateAccrual".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Accrual> entityWithMetadata) {
        Accrual entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Calculates both the day count fraction and the interest amount.
     * This combines the logic from DeriveDayCountFractionProcessor and CalculateAccrualAmountProcessor.
     */
    private EntityWithMetadata<Accrual> calculateDayCountFractionAndInterest(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Calculating day count fraction and interest amount for accrual: {}", accrual.getAccrualId());

        // Step 1: Calculate day count fraction
        LocalDate asOfDate = accrual.getAsOfDate();
        DayCountConvention convention = accrual.getDayCountConvention();

        if (asOfDate == null) {
            logger.error("AsOfDate is null for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("AsOfDate is required for day count calculation");
        }

        if (convention == null) {
            logger.error("DayCountConvention is null for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("DayCountConvention is required for day count calculation");
        }

        // Calculate the previous business day (for daily accrual, this is typically asOfDate - 1)
        // In a production system, this would use a business calendar service
        LocalDate previousDate = asOfDate.minusDays(1);

        // Calculate day count fraction based on convention
        BigDecimal dayCountFraction = switch (convention) {
            case ACT_360 -> calculateActual360(previousDate, asOfDate);
            case ACT_365 -> calculateActual365(previousDate, asOfDate);
            case THIRTY_360 -> calculateThirty360(previousDate, asOfDate);
        };

        // Set the calculated fraction on the accrual
        accrual.setDayCountFraction(dayCountFraction);

        logger.info("Day count fraction calculated for accrual {}: {} (convention: {})",
            accrual.getAccrualId(), dayCountFraction, convention);

        // Step 2: Calculate interest amount
        BigDecimal principal = accrual.getPrincipalSnapshot() != null ?
            accrual.getPrincipalSnapshot().getAmount() : null;
        String loanId = accrual.getLoanId();

        // Validate required fields
        if (principal == null) {
            logger.error("Principal is null for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("Principal is required for interest calculation");
        }

        if (loanId == null || loanId.trim().isEmpty()) {
            logger.error("LoanId is null or empty for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("LoanId is required to retrieve APR");
        }

        // Retrieve APR from the loan entity
        BigDecimal apr = retrieveAprFromLoan(loanId, accrual.getAccrualId());

        // Calculate interest amount: principal × APR × dayCountFraction
        BigDecimal interestAmount = principal
            .multiply(apr)
            .multiply(dayCountFraction)
            .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

        // Set the calculated amount on the accrual
        accrual.setInterestAmount(interestAmount);

        logger.info("Interest amount calculated for accrual {}: {} (principal: {}, APR: {}, fraction: {})",
            accrual.getAccrualId(), interestAmount, principal, apr, dayCountFraction);

        // Log warning if interest amount is zero or negative
        if (interestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Interest amount is zero or negative for accrual {}: {}",
                accrual.getAccrualId(), interestAmount);
        }

        return entityWithMetadata;
    }

    /**
     * Calculates day count fraction using ACT/360 convention.
     * Formula: (actual days between dates) / 360
     */
    private BigDecimal calculateActual360(LocalDate startDate, LocalDate endDate) {
        long actualDays = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal days = BigDecimal.valueOf(actualDays);
        BigDecimal divisor = BigDecimal.valueOf(360);

        // Use high precision for the fraction
        BigDecimal fraction = days.divide(divisor, 10, RoundingMode.HALF_UP);

        logger.debug("ACT/360: {} days / 360 = {}", actualDays, fraction);
        return fraction;
    }

    /**
     * Calculates day count fraction using ACT/365 convention.
     * Formula: (actual days between dates) / 365
     */
    private BigDecimal calculateActual365(LocalDate startDate, LocalDate endDate) {
        long actualDays = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal days = BigDecimal.valueOf(actualDays);
        BigDecimal divisor = BigDecimal.valueOf(365);

        // Use high precision for the fraction
        BigDecimal fraction = days.divide(divisor, 10, RoundingMode.HALF_UP);

        logger.debug("ACT/365: {} days / 365 = {}", actualDays, fraction);
        return fraction;
    }

    /**
     * Calculates day count fraction using 30/360 convention.
     * Formula: ((Y2-Y1)*360 + (M2-M1)*30 + (D2-D1)) / 360
     *
     * This is a simplified implementation. Production systems may need to handle
     * various 30/360 variants (US, European, etc.)
     */
    private BigDecimal calculateThirty360(LocalDate startDate, LocalDate endDate) {
        int y1 = startDate.getYear();
        int m1 = startDate.getMonthValue();
        int d1 = startDate.getDayOfMonth();

        int y2 = endDate.getYear();
        int m2 = endDate.getMonthValue();
        int d2 = endDate.getDayOfMonth();

        // Adjust day values according to 30/360 rules
        // If D1 is 31, change to 30
        if (d1 == 31) {
            d1 = 30;
        }

        // If D2 is 31 and D1 is 30 or 31, change D2 to 30
        if (d2 == 31 && d1 >= 30) {
            d2 = 30;
        }

        // Calculate the number of days using 30/360 formula
        int days = (y2 - y1) * 360 + (m2 - m1) * 30 + (d2 - d1);

        BigDecimal daysBD = BigDecimal.valueOf(days);
        BigDecimal divisor = BigDecimal.valueOf(360);

        // Use high precision for the fraction
        BigDecimal fraction = daysBD.divide(divisor, 10, RoundingMode.HALF_UP);

        logger.debug("30/360: {} days / 360 = {}", days, fraction);
        return fraction;
    }

    /**
     * Retrieves the APR from the loan entity.
     */
    private BigDecimal retrieveAprFromLoan(String loanId, String accrualId) {
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
            logger.error("Error retrieving loan {} for accrual {}: {}",
                loanId, accrualId, e.getMessage());
            throw new IllegalStateException("Failed to retrieve loan: " + loanId, e);
        }

        if (loanWithMetadata == null) {
            logger.error("Loan {} not found for accrual: {}", loanId, accrualId);
            throw new IllegalStateException("Loan not found: " + loanId);
        }

        Loan loan = loanWithMetadata.entity();
        BigDecimal apr = loan.getApr();

        if (apr == null) {
            logger.error("APR is null for loan {} (accrual: {})", loanId, accrualId);
            throw new IllegalStateException("APR is required for interest calculation");
        }

        logger.debug("Retrieved APR {} from loan {} for accrual {}", apr, loanId, accrualId);
        return apr;
    }
}

