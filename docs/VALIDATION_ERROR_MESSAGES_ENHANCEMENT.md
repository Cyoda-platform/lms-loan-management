# Validation Error Messages Enhancement

## Overview

This document describes the enhancement made to provide detailed validation error messages throughout the LMS codebase. Previously, validation criteria returned generic messages like "Payment entity is not valid". Now, they return specific, actionable error messages that tell the caller exactly why validation failed.

## Problem Statement

**Before Enhancement:**
- Validation criteria called `entity.isValid()` which returned only `true` or `false`
- When validation failed, criteria returned generic messages like:
  - "Payment entity is not valid"
  - "Loan entity is not valid"
  - "SettlementQuote entity is not valid"
- Users and developers had no visibility into **why** validation failed
- Debugging validation failures required examining logs or stepping through code

**After Enhancement:**
- Each entity provides a `getValidationFailureReason()` method
- This method returns a specific, human-readable error message
- Validation criteria use this method to provide detailed failure reasons
- Error messages are attached to `EvaluationOutcome` and propagated to callers

## Implementation Pattern

### Entity Enhancement

Each entity implementing `CyodaEntity` now provides:

```java
/**
 * Returns a detailed reason why the entity is invalid.
 * This method should only be called when isValid() returns false.
 *
 * @param metadata the entity metadata
 * @return a human-readable string describing the validation failure
 */
public String getValidationFailureReason(EntityMetadata metadata) {
    // Check each validation condition and return specific error message
    if (field1 == null || field1.trim().isEmpty()) {
        return "Field1 is required";
    }
    if (field2 == null) {
        return "Field2 is required";
    }
    if (field2.compareTo(BigDecimal.ZERO) <= 0) {
        return "Field2 must be positive (got: " + field2 + ")";
    }
    // ... more checks ...
    return "Entity validation failed for unknown reason";
}
```

### Criterion Enhancement

Validation criteria now use the new method:

```java
public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Entity> context) {
    Entity entity = context.entityWithMetadata().entity();

    if (entity == null) {
        return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
    }

    if (!entity.isValid(context.entityWithMetadata().metadata())) {
        String reason = entity.getValidationFailureReason(context.entityWithMetadata().metadata());
        logger.warn("Entity is not valid: {} - {}", entity.getId(), reason);
        return EvaluationOutcome.fail(reason, StandardEvalReasonCategories.VALIDATION_FAILURE);
    }

    // Additional business rule validations...
    return EvaluationOutcome.success();
}
```

## Enhanced Entities

### 1. Payment Entity

**File:** `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`

**Validation Checks:**
- Payment ID is required
- Loan ID is required
- Payer Party ID is required
- Payment amount is required
- Payment amount must be positive (includes actual value in error)
- Currency is required
- Value date is required
- Received date is required

**Example Error Messages:**
- "Payment ID is required"
- "Payment amount must be positive (got: -100.00)"
- "Currency is required"

### 2. Loan Entity

**File:** `src/main/java/com/java_template/application/entity/loan/version_1/Loan.java`

**Validation Checks:**
- Loan ID is required
- Agreement ID is required
- Party ID is required
- Principal amount is required
- Principal amount must be positive (includes actual value)
- APR is required
- APR must be positive (includes actual value)
- Term months is required
- Term must be 12, 24, or 36 months (includes actual value)
- Funding date is required
- Maturity date is required

**Example Error Messages:**
- "Loan ID is required"
- "Principal amount must be positive (got: 0)"
- "Term must be 12, 24, or 36 months (got: 48)"
- "APR must be positive (got: 0.00)"

### 3. SettlementQuote Entity

**File:** `src/main/java/com/java_template/application/entity/settlement_quote/version_1/SettlementQuote.java`

**Validation Checks:**
- Quote ID is required
- Loan ID is required
- Settlement date is required
- Expiration date is required
- Requested by is required
- Total amount due is required
- Total amount due must be positive (includes actual value)
- Currency is required

**Example Error Messages:**
- "Quote ID is required"
- "Total amount due must be positive (got: 0.00)"
- "Settlement date is required"

### 4. Party Entity

**File:** `src/main/java/com/java_template/application/entity/party/version_1/Party.java`

**Validation Checks:**
- Party ID is required
- Legal name is required
- Jurisdiction is required

**Example Error Messages:**
- "Party ID is required"
- "Legal name is required"
- "Jurisdiction is required"

### 5. Accrual Entity

**File:** `src/main/java/com/java_template/application/entity/accrual/version_1/Accrual.java`

**Validation Checks:**
- Loan ID is required
- As-of date is required
- Currency is required
- Currency must be valid ISO-4217 code (includes actual value)
- Journal entries must be balanced for POSTED accruals
- All journal entries must be valid (includes index of invalid entry)

**Example Error Messages:**
- "Loan ID is required"
- "Currency must be a valid ISO-4217 code (got: XXX)"
- "Journal entries must be balanced (debits must equal credits) for POSTED accruals"
- "Journal entry at index 2 is invalid"

### 6. GLBatch Entity

**File:** `src/main/java/com/java_template/application/entity/gl_batch/version_1/GLBatch.java`

**Validation Checks:**
- Batch ID is required
- Period is required

**Example Error Messages:**
- "Batch ID is required"
- "Period is required"

### 7. EODAccrualBatch Entity

**File:** `src/main/java/com/java_template/application/entity/accrual/version_1/EODAccrualBatch.java`

**Validation Checks:**
- As-of date is required
- Batch mode is required
- Initiated by is required
- Reason code is required when mode is BACKDATED
- Metrics object must be initialized

**Example Error Messages:**
- "As-of date is required"
- "Batch mode is required"
- "Reason code is required when mode is BACKDATED"
- "Metrics object must be initialized"

## Enhanced Criteria

### Criteria Updated to Use Detailed Messages

1. **PaymentValidationCriterion** - `src/main/java/com/java_template/application/criterion/PaymentValidationCriterion.java`
2. **NewLoanValidationCriterion** - `src/main/java/com/java_template/application/criterion/NewLoanValidationCriterion.java`
3. **SettlementQuoteValidationCriterion** - `src/main/java/com/java_template/application/criterion/SettlementQuoteValidationCriterion.java`
4. **NewPartyValidationCriterion** - `src/main/java/com/java_template/application/criterion/NewPartyValidationCriterion.java`
5. **GLBatchValidationCriterion** - `src/main/java/com/java_template/application/criterion/glbatch/GLBatchValidationCriterion.java`

### Criteria That Don't Need Updates

Some criteria don't call `isValid()` directly and instead perform composite validations:
- **AccrualValidationCriterion** - Delegates to specialized criteria (IsBusinessDay, LoanActiveOnDate, etc.)
- **EODAccrualBatchValidationCriterion** - Delegates to specialized criteria

These criteria already provide detailed error messages through their specialized sub-criteria.

## Benefits

### 1. Improved User Experience
- Users see exactly what's wrong with their data
- No need to contact support for common validation errors
- Self-service error resolution

### 2. Faster Debugging
- Developers can identify validation issues immediately
- No need to step through code or examine logs
- Error messages appear in API responses and workflow warnings

### 3. Better Audit Trail
- Validation errors are logged with specific reasons
- Easier to track down data quality issues
- Better compliance and troubleshooting

### 4. Consistent Error Reporting
- All entities follow the same pattern
- Predictable error message format
- Easy to extend to new entities

## Usage Examples

### Example 1: Payment Validation Failure

**Before:**
```
EvaluationOutcome.fail("Payment entity is not valid", VALIDATION_FAILURE)
```

**After:**
```
EvaluationOutcome.fail("Payment amount must be positive (got: -50.00)", VALIDATION_FAILURE)
```

### Example 2: Loan Validation Failure

**Before:**
```
EvaluationOutcome.fail("Loan entity is not valid", VALIDATION_FAILURE)
```

**After:**
```
EvaluationOutcome.fail("Term must be 12, 24, or 36 months (got: 60)", VALIDATION_FAILURE)
```

### Example 3: Accrual Validation Failure

**Before:**
```
EvaluationOutcome.fail("Accrual entity is not valid", VALIDATION_FAILURE)
```

**After:**
```
EvaluationOutcome.fail("Currency must be a valid ISO-4217 code (got: INVALID)", VALIDATION_FAILURE)
```

## Testing Recommendations

1. **Unit Tests**: Test `getValidationFailureReason()` for each entity
   - Test each validation condition
   - Verify correct error message is returned
   - Verify fallback message for unknown failures

2. **Integration Tests**: Test criteria with invalid entities
   - Verify detailed error messages appear in EvaluationOutcome
   - Verify error messages are attached to workflow warnings
   - Verify error messages appear in API responses

3. **End-to-End Tests**: Test user workflows with invalid data
   - Verify users see helpful error messages
   - Verify error messages enable self-service resolution

## Future Enhancements

1. **Internationalization**: Support multiple languages for error messages
2. **Error Codes**: Add structured error codes alongside messages
3. **Field-Level Errors**: Return multiple validation errors at once
4. **Validation Context**: Include more context in error messages (e.g., entity ID, state)
5. **Custom Validators**: Allow entities to register custom validation rules

## Conclusion

This enhancement significantly improves the developer and user experience by providing specific, actionable validation error messages throughout the LMS codebase. The pattern is consistent, easy to maintain, and can be extended to new entities as they are added to the system.

