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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ABOUTME: This processor generates an export file (CSV format) for the GL batch.
 * 
 * Execution Mode: SYNC
 * Transition: maker_approved -> exported
 * 
 * Logic:
 * 1. Format GL lines as CSV
 * 2. Generate file path/name
 * 3. Store file metadata on batch
 * 
 * In a real implementation, this would write to a file system or S3.
 */
@Component
public class GenerateExportFile implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateExportFile.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public GenerateExportFile(SerializerFactory serializerFactory) {
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

        logger.debug("Generating export file for GLBatch: {}", batch.getBatchId());

        // Generate CSV content
        String csvContent = generateCSV(batch);
        
        // Generate file path
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("GL_BATCH_%s_%s.csv", batch.getPeriod(), timestamp);
        String filePath = "/exports/gl/" + fileName;

        // In a real implementation, write to file system or S3
        logger.info("Generated export file: {} ({} bytes)", filePath, csvContent.length());
        logger.debug("CSV content preview:\n{}", csvContent.substring(0, Math.min(500, csvContent.length())));

        // Store file metadata
        batch.setExportFilePath(filePath);
        batch.setExportedAt(LocalDateTime.now());

        logger.info("Export file generated for batch {}: {}", batch.getBatchId(), filePath);

        return entityWithMetadata;
    }

    /**
     * Generates CSV content from GL lines.
     */
    private String generateCSV(GLBatch batch) {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("GL_Account,Description,Type,Amount,Currency\n");
        
        // Lines
        List<GLBatch.GLLine> glLines = batch.getGlLines();
        if (glLines != null) {
            for (GLBatch.GLLine line : glLines) {
                csv.append(String.format("%s,%s,%s,%s,%s\n",
                    escapeCsv(line.getGlAccount()),
                    escapeCsv(line.getDescription()),
                    escapeCsv(line.getType()),
                    line.getAmount(),
                    escapeCsv(line.getCurrency())
                ));
            }
        }
        
        // Footer with control totals
        if (batch.getControlTotals() != null) {
            csv.append("\n");
            csv.append(String.format("Total Debits,,%s\n", batch.getControlTotals().getTotalDebits()));
            csv.append(String.format("Total Credits,,%s\n", batch.getControlTotals().getTotalCredits()));
            csv.append(String.format("Line Count,,%d\n", batch.getControlTotals().getLineCount()));
        }
        
        return csv.toString();
    }

    /**
     * Escapes CSV values.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

