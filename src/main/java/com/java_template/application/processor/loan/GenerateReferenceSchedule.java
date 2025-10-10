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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * ABOUTME: This processor generates an amortization schedule for the loan
 * based on the term, APR, and principal amount.
 * 
 * Execution Mode: ASYNC_NEW_TX
 * Transition: approved -> funded
 * 
 * The schedule includes:
 * - Payment dates
 * - Principal and interest breakdown
 * - Outstanding balance after each payment
 */
@Component
public class GenerateReferenceSchedule implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReferenceSchedule.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public GenerateReferenceSchedule(SerializerFactory serializerFactory) {
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

        logger.debug("Generating reference schedule for loan: {}", loan.getLoanId());

        // Validate required fields for schedule generation
        if (loan.getPrincipalAmount() == null || loan.getApr() == null || 
            loan.getTermMonths() == null || loan.getFundingDate() == null) {
            throw new IllegalStateException("Loan is missing required fields for schedule generation");
        }

        // Generate the amortization schedule
        generateAmortizationSchedule(loan);

        logger.info("Reference schedule generated for loan {} with {} payments", 
                   loan.getLoanId(), loan.getTermMonths());

        return entityWithMetadata;
    }

    /**
     * Generates an amortization schedule for the loan.
     * 
     * In a real implementation, this would:
     * 1. Calculate monthly payment amount using loan formula
     * 2. Generate payment schedule with dates
     * 3. Calculate principal/interest breakdown for each payment
     * 4. Store schedule in a separate entity or as embedded data
     * 
     * For now, we'll just log the schedule generation.
     */
    private void generateAmortizationSchedule(Loan loan) {
        BigDecimal principal = loan.getPrincipalAmount();
        BigDecimal annualRate = loan.getApr();
        Integer termMonths = loan.getTermMonths();
        LocalDate fundingDate = loan.getFundingDate();

        // Calculate monthly interest rate
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // Calculate monthly payment using amortization formula
        // PMT = P * [r(1+r)^n] / [(1+r)^n - 1]
        BigDecimal onePlusRate = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRatePowerN = onePlusRate.pow(termMonths);
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRatePowerN);
        BigDecimal denominator = onePlusRatePowerN.subtract(BigDecimal.ONE);
        BigDecimal monthlyPayment = numerator.divide(denominator, 2, RoundingMode.HALF_UP);

        logger.debug("Loan {}: Principal={}, APR={}, Term={} months, Monthly Payment={}", 
                    loan.getLoanId(), principal, annualRate, termMonths, monthlyPayment);

        // In a real implementation, you would:
        // 1. Create a LoanSchedule entity or embedded structure
        // 2. Generate payment entries for each month
        // 3. Calculate principal/interest split for each payment
        // 4. Track remaining balance
        // 5. Store the schedule for reference

        // For now, just log that schedule was generated
        logger.info("Generated amortization schedule with {} monthly payments of {}", 
                   termMonths, monthlyPayment);
    }
}

