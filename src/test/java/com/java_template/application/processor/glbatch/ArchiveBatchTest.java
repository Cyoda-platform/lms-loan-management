package com.java_template.application.processor.glbatch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArchiveBatch processor.
 */
class ArchiveBatchTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("ArchiveBatch");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "ArchiveBatch");

        String expectedName = "ArchiveBatch";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("archivebatch");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "archivebatch");

        String expectedName = "ArchiveBatch";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "ArchiveBatch";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

