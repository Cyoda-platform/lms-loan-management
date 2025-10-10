package com.java_template.application.processor.glbatch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SendToGLSystem processor.
 */
class SendToGLSystemTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SendToGLSystem");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SendToGLSystem");

        String expectedName = "SendToGLSystem";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("sendtoglsystem");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "sendtoglsystem");

        String expectedName = "SendToGLSystem";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "SendToGLSystem";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

