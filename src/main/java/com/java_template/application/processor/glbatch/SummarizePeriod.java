package com.java_template.application.processor.glbatch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.application.entity.payment.version_1.Payment;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ABOUTME: This processor aggregates all sub-ledger entries (Accruals and Payments)
 * for a given period and creates summarized GL lines grouped by GL account.
 * 
 * Execution Mode: ASYNC_NEW_TX
 * Transition: open -> prepared
 * 
 * Logic:
 * 1. Parse the period (e.g., "2025-09")
 * 2. Query all Accruals for the period
 * 3. Query all Payments for the period
 * 4. Aggregate by GL account
 * 5. Create GLLine entries
 */
@Component
public class SummarizePeriod implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SummarizePeriod.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SummarizePeriod(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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

        logger.debug("Summarizing period for GLBatch: {} (period: {})", batch.getBatchId(), batch.getPeriod());

        // Parse period
        YearMonth period = YearMonth.parse(batch.getPeriod(), DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate startDate = period.atDay(1);
        LocalDate endDate = period.atEndOfMonth();

        logger.debug("Period range: {} to {}", startDate, endDate);

        // Query and aggregate accruals
        List<Accrual> accruals = queryAccrualsForPeriod(startDate, endDate);
        logger.info("Found {} accruals for period {}", accruals.size(), batch.getPeriod());

        // Query and aggregate payments
        List<Payment> payments = queryPaymentsForPeriod(startDate, endDate);
        logger.info("Found {} payments for period {}", payments.size(), batch.getPeriod());

        // Aggregate into GL lines
        List<GLBatch.GLLine> glLines = aggregateToGLLines(accruals, payments);
        batch.setGlLines(glLines);

        logger.info("Generated {} GL lines for batch {}", glLines.size(), batch.getBatchId());

        return entityWithMetadata;
    }

    /**
     * Queries all accruals for the given period.
     */
    private List<Accrual> queryAccrualsForPeriod(LocalDate startDate, LocalDate endDate) {
        try {
            ModelSpec accrualModelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);

            List<EntityWithMetadata<Accrual>> accruals = 
                entityService.findAll(accrualModelSpec, Accrual.class);

            // Filter by date range
            return accruals.stream()
                .map(EntityWithMetadata::entity)
                .filter(accrual -> accrual.getAsOfDate() != null)
                .filter(accrual -> !accrual.getAsOfDate().isBefore(startDate) && 
                                  !accrual.getAsOfDate().isAfter(endDate))
                .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error querying accruals for period {} to {}: {}", 
                        startDate, endDate, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Queries all payments for the given period.
     */
    private List<Payment> queryPaymentsForPeriod(LocalDate startDate, LocalDate endDate) {
        try {
            ModelSpec paymentModelSpec = new ModelSpec()
                .withName(Payment.ENTITY_NAME)
                .withVersion(Payment.ENTITY_VERSION);

            List<EntityWithMetadata<Payment>> payments = 
                entityService.findAll(paymentModelSpec, Payment.class);

            // Filter by value date range
            return payments.stream()
                .map(EntityWithMetadata::entity)
                .filter(payment -> payment.getValueDate() != null)
                .filter(payment -> !payment.getValueDate().isBefore(startDate) && 
                                  !payment.getValueDate().isAfter(endDate))
                .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error querying payments for period {} to {}: {}", 
                        startDate, endDate, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Aggregates accruals and payments into GL lines grouped by GL account.
     */
    private List<GLBatch.GLLine> aggregateToGLLines(List<Accrual> accruals, List<Payment> payments) {
        List<GLBatch.GLLine> glLines = new ArrayList<>();
        Map<String, BigDecimal> accountTotals = new HashMap<>();

        // Process accruals - create debit to Interest Receivable and credit to Interest Income
        BigDecimal totalInterestAccrued = accruals.stream()
            .filter(a -> a.getInterestAmount() != null)
            .map(Accrual::getInterestAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalInterestAccrued.compareTo(BigDecimal.ZERO) > 0) {
            // Debit Interest Receivable
            glLines.add(createGLLine("1100-Interest-Receivable", "Interest accrued for period", 
                                    "DEBIT", totalInterestAccrued, "USD"));
            
            // Credit Interest Income
            glLines.add(createGLLine("4000-Interest-Income", "Interest income for period", 
                                    "CREDIT", totalInterestAccrued, "USD"));
        }

        // Process payments - create debit to Cash and credit to Loan Principal
        BigDecimal totalPaymentsReceived = payments.stream()
            .filter(p -> p.getPaymentAmount() != null)
            .map(Payment::getPaymentAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaymentsReceived.compareTo(BigDecimal.ZERO) > 0) {
            // Debit Cash
            glLines.add(createGLLine("1010-Cash", "Payments received for period", 
                                    "DEBIT", totalPaymentsReceived, "USD"));
            
            // Credit Loan Principal (simplified - in reality would split interest/principal)
            glLines.add(createGLLine("1200-Loan-Principal", "Principal reductions for period", 
                                    "CREDIT", totalPaymentsReceived, "USD"));
        }

        return glLines;
    }

    /**
     * Creates a GL line entry.
     */
    private GLBatch.GLLine createGLLine(String glAccount, String description, 
                                       String type, BigDecimal amount, String currency) {
        GLBatch.GLLine line = new GLBatch.GLLine();
        line.setGlLineId(UUID.randomUUID().toString());
        line.setGlAccount(glAccount);
        line.setDescription(description);
        line.setType(type);
        line.setAmount(amount);
        line.setCurrency(currency);
        return line;
    }
}

