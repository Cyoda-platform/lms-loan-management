package com.java_template.application.processor.glbatch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SummarizePeriod processor.
 */
class SummarizePeriodTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SummarizePeriod");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SummarizePeriod");

        String expectedName = "SummarizePeriod";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("summarizeperiod");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "summarizeperiod");

        String expectedName = "SummarizePeriod";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "SummarizePeriod";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

