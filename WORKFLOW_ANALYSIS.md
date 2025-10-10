# LMS Workflow Analysis: Current vs Specification

## Executive Summary

This document analyzes the current workflow configurations in `src/main/resources/workflow` against the LMS_SPECIFICATION.md requirements. The analysis identifies gaps, missing processors/criteria, and required corrections.

**Key Findings:**
- ✅ **Validation Pattern**: All entities correctly implement the validation error pattern (must be preserved)
- ⚠️ **Loan Workflow**: Requires corrections to state transitions and missing processors
- ❌ **GLBatch Workflow**: Completely missing - needs to be created
- ✅ **Payment, Accrual, SettlementQuote, EODAccrualBatch**: Largely compliant with minor notes

---

## Validation Error Pattern (MUST BE PRESERVED)

All current workflows implement this pattern, which must be maintained:

```
initial state
  ├─> [Positive Validation Criterion] -> main lifecycle state (e.g., DRAFT, ACTIVE, CAPTURED)
  └─> [Negative Validation Criterion] -> validation_error state
        └─> [Manual FIX transition] -> back to initial state
```

**Components:**
- `{Entity}ValidationCriterion` - returns true if entity is valid
- `{Entity}ValidationFailedCriterion` - returns true if entity is invalid
- `Attach{Entity}ValidationErrorProcessor` - populates `validationErrorReason` field
- `ClearValidationErrorReasonProcessor` - clears error and allows retry (shared across entities)

**Benefits:**
- Complete audit trail of validation failures
- User-friendly error correction workflow
- Prevents invalid entities from entering main lifecycle

---

## Entity-by-Entity Analysis

### 1. LOAN ENTITY

#### Current State Diagram
```
initial -> draft -> approval_pending -> approved -> funded -> active -> matured -> settled -> closed
                                    \-> rejected
```

#### Specification State Diagram
```
initial -> DRAFT -> APPROVAL_PENDING -> APPROVED -> FUNDED -> ACTIVE -> CLOSED
                                    \-> DRAFT                        \-> SETTLED
```

#### Issues Identified

**Issue 1: Reject Transition Target**
- **Current**: `approval_pending` -> `rejected` (terminal state)
- **Spec**: `approval_pending` -> `DRAFT` (allows correction and resubmission)
- **Impact**: Users cannot correct and resubmit rejected loans
- **Fix**: Change reject transition to target `draft` state, remove `rejected` state

**Issue 2: Matured State**
- **Current**: Has `matured` state between `active` and `settled`
- **Spec**: No `matured` state - goes directly from `ACTIVE` to `CLOSED`
- **Impact**: Extra state not in specification
- **Fix**: Remove `matured` state, transition directly from `active` to `closed`

**Issue 3: Settlement Transition**
- **Current**: Manual `settle_early` transition from `active` to `settled`
- **Spec**: Automatic transition when "Accepted SettlementQuote exists and is paid"
- **Impact**: Settlement should be automatic, not manual
- **Fix**: Add criterion-based automatic transition

**Issue 4: Settled State**
- **Current**: `settled` -> `closed` transition exists
- **Spec**: `SETTLED` is a terminal state (no outgoing transitions)
- **Impact**: Incorrect lifecycle
- **Fix**: Remove transition from `settled` to `closed`, make `settled` terminal

**Issue 5: Closed State Criterion**
- **Current**: `mature_loan` transition uses only `LoanMaturityDateReachedCriterion`
- **Spec**: Should check "maturityDate reached AND outstandingPrincipal == 0"
- **Impact**: Loan could close with outstanding balance
- **Fix**: Create `LoanMaturityAndFullyPaidCriterion` combining both checks

#### Missing Processors

| Processor Name | Execution Mode | Transition | Purpose |
|----------------|----------------|------------|---------|
| `StampedApproval` | SYNC | approval_pending -> approved | Records actor/role metadata for audit (maker/checker) |
| `GenerateReferenceSchedule` | ASYNC | approved -> funded | Generates amortization schedule on funding |
| `CloseLoan` | SYNC | active -> closed | Finalizes loan closure (may exist, needs verification) |

**Note**: `ProcessEarlySettlement` exists but may need to be renamed to `ApplySettlement` per spec

#### Missing Criteria

| Criterion Name | Transition | Logic |
|----------------|------------|-------|
| `LoanMaturityAndFullyPaidCriterion` | active -> closed | Returns true if `maturityDate <= now() AND outstandingPrincipal == 0` |
| `SettlementQuoteAcceptedAndPaidCriterion` | active -> settled | Returns true if accepted SettlementQuote exists and payment received |

#### Corrected Workflow Structure

```json
{
  "states": {
    "initial": {
      "transitions": [
        {"name": "create_loan", "next": "draft", "criterion": "NewLoanValidationCriterion"},
        {"name": "validation_failed", "next": "validation_error", "criterion": "NewLoanValidationFailedCriterion", "processors": ["AttachNewLoanValidationErrorProcessor"]}
      ]
    },
    "validation_error": {
      "transitions": [
        {"name": "FIX", "next": "initial", "manual": true, "processors": ["ClearValidationErrorReasonProcessor"]}
      ]
    },
    "draft": {
      "transitions": [
        {"name": "submit_for_approval", "next": "approval_pending", "manual": true, "processors": ["PrepareForApproval"]},
        {"name": "update_loan", "next": "initial", "manual": true}
      ]
    },
    "approval_pending": {
      "transitions": [
        {"name": "approve_loan", "next": "approved", "manual": true, "processors": ["StampedApproval"]},
        {"name": "reject_loan", "next": "draft", "manual": true}
      ]
    },
    "approved": {
      "transitions": [
        {"name": "fund_loan", "next": "funded", "manual": true, "processors": ["SetInitialBalances", "GenerateReferenceSchedule"]}
      ]
    },
    "funded": {
      "transitions": [
        {"name": "activate_loan", "next": "active", "manual": false, "criterion": "LoanFundingDateReachedCriterion"}
      ]
    },
    "active": {
      "transitions": [
        {"name": "close_loan", "next": "closed", "manual": false, "criterion": "LoanMaturityAndFullyPaidCriterion", "processors": ["CloseLoan"]},
        {"name": "settle_loan", "next": "settled", "manual": false, "criterion": "SettlementQuoteAcceptedAndPaidCriterion", "processors": ["ApplySettlement"]}
      ]
    },
    "closed": {
      "transitions": []
    },
    "settled": {
      "transitions": []
    }
  }
}
```

---

### 2. PAYMENT ENTITY

#### Status: ✅ COMPLIANT

The Payment workflow correctly implements the specification with validation pattern.

**Current Flow:**
```
initial -> captured -> matched -> allocated -> posted
                   \-> unmatched -> matched
                                \-> returned
```

**Spec Requirements:** ✅ Met
- CAPTURED state ✅
- Auto match to MATCHED ✅
- Auto allocate to ALLOCATED ✅
- Auto post to POSTED ✅

**Additional States (Reasonable Extensions):**
- `unmatched` - for exception handling when auto-match fails
- `returned` - for payments that cannot be matched and must be returned

**Processors Present:**
- ✅ `MatchPaymentToLoan`
- ✅ `AllocatePaymentFunds`
- ✅ `PostPaymentEntries`
- ✅ `ManualMatchPayment`
- ✅ `ReturnPayment`

**No changes required.**

---

### 3. ACCRUAL ENTITY

#### Status: ✅ COMPLIANT

The Accrual workflow correctly implements the specification with validation pattern.

**Current Flow:**
```
NEW -> ELIGIBLE -> CALCULATED -> POSTED -> SUPERSEDED
                \-> FAILED
```

**Spec Requirements:** ✅ Met
- Daily interest calculation ✅
- Sub-ledger posting ✅
- Loan balance update ✅

**Processors Present:**
- ✅ `DeriveDayCountFraction`
- ✅ `CalculateAccrualAmount` (implements ComputeDailyInterest from spec)
- ✅ `WriteAccrualJournalEntries`
- ✅ `UpdateLoanAccruedInterest`

**Additional Features (Good Additions):**
- `SUPERSEDED` state for corrections/rebooking
- `RequiresRebook` criterion for handling corrections

**No changes required.**

---

### 4. PARTY ENTITY

#### Status: ✅ MOSTLY COMPLIANT

The Party workflow implements basic CRUD with validation pattern.

**Current Flow:**
```
initial -> active -> inactive
              \-> initial (update)
```

**Note:** The `update_party` transition going back to `initial` is unusual but not explicitly wrong. This may warrant discussion but doesn't violate the spec.

**No critical changes required.**

---

### 5. SETTLEMENT QUOTE ENTITY

#### Status: ✅ COMPLIANT

The SettlementQuote workflow correctly implements the specification with validation pattern.

**Current Flow:**
```
initial -> calculating -> quoted -> accepted
                              \-> expired -> calculating
```

**Processors Present:**
- ✅ `CalculateSettlementAmount`
- ✅ `AcceptSettlementQuote`

**Criteria Present:**
- ✅ `SettlementQuoteExpiredCriterion`

**No changes required.**

---

### 6. EOD ACCRUAL BATCH ENTITY

#### Status: ✅ COMPLIANT (Enhanced)

The EODAccrualBatch workflow is more sophisticated than the spec but production-ready.

**Current Flow:**
```
REQUESTED -> VALIDATED -> SNAPSHOT_TAKEN -> GENERATING -> POSTING_COMPLETE -> RECONCILING -> COMPLETED
                                                                          \-> CASCADING -> RECONCILING
```

**Additional Features Beyond Spec:**
- Backdated accrual support
- Snapshot capture for effective dating
- Cascade recalculation for backdated runs
- Reconciliation and reporting

**These are valuable production features. No changes required.**

---

### 7. GLBATCH ENTITY

#### Status: ❌ MISSING - MUST BE CREATED

The GLBatch workflow is completely missing and must be created.

#### Specification Requirements

**From LMS_SPECIFICATION.md Section 3.2.4:**

```
stateDiagram-v2
    [*] --> OPEN
    OPEN --> PREPARED: Prepare
    PREPARED --> EXPORTED: Export (Maker/Checker)
    EXPORTED --> POSTED: Acknowledgment Received
    POSTED --> ARCHIVED: Archive
    ARCHIVED --> [*]
```

**From User Story 7:**
- Finance Manager initiates month-end process
- System creates GLBatch in PREPARED state
- Batch contains aggregated journal lines as an embedded list (not separate entities)
- Debits must equal credits (control totals)
- Maker/checker approval required for export
- Export generates file (CSV) for GL system
- GL system sends acknowledgment
- Batch is archived after posting

**Design Note:** GL lines are stored as an embedded array/list within the GLBatch entity, not as separate entities with their own lifecycle.

#### Required Workflow Structure

```json
{
  "version": "1.0",
  "name": "GLBatch",
  "desc": "Month-end GL batch workflow with maker/checker controls",
  "initialState": "initial",
  "active": true,
  "states": {
    "initial": {
      "transitions": [
        {"name": "create_batch", "next": "open", "criterion": "GLBatchValidationCriterion"},
        {"name": "validation_failed", "next": "validation_error", "criterion": "GLBatchValidationFailedCriterion", "processors": ["AttachGLBatchValidationErrorProcessor"]}
      ]
    },
    "validation_error": {
      "transitions": [
        {"name": "FIX", "next": "initial", "manual": true, "processors": ["ClearValidationErrorReasonProcessor"]}
      ]
    },
    "open": {
      "transitions": [
        {"name": "prepare_batch", "next": "prepared", "manual": true, "processors": ["SummarizePeriod", "CalculateControlTotals"]}
      ]
    },
    "prepared": {
      "transitions": [
        {"name": "approve_export_maker", "next": "maker_approved", "manual": true, "processors": ["RecordMakerApproval"]},
        {"name": "reject_batch", "next": "open", "manual": true}
      ]
    },
    "maker_approved": {
      "transitions": [
        {"name": "approve_export_checker", "next": "exported", "manual": true, "criterion": "MakerCheckerDifferentUsers", "processors": ["RecordCheckerApproval", "GenerateExportFile", "SendToGLSystem"]},
        {"name": "reject_batch", "next": "open", "manual": true}
      ]
    },
    "exported": {
      "transitions": [
        {"name": "receive_acknowledgment", "next": "posted", "manual": false, "criterion": "GLAcknowledgmentReceived"}
      ]
    },
    "posted": {
      "transitions": [
        {"name": "archive_batch", "next": "archived", "manual": true, "processors": ["ArchiveBatch"]}
      ]
    },
    "archived": {
      "transitions": []
    }
  }
}
```

#### Missing Processors for GLBatch

| Processor Name | Execution Mode | Purpose |
|----------------|----------------|---------|
| `GLBatchValidationCriterion` | - | Validates batch can be created (period not already processed) |
| `GLBatchValidationFailedCriterion` | - | Inverse of validation criterion |
| `AttachGLBatchValidationErrorProcessor` | SYNC | Populates validationErrorReason field |
| `SummarizePeriod` | ASYNC_NEW_TX | Aggregates all sub-ledger entries for the period |
| `CalculateControlTotals` | SYNC | Calculates total debits, credits, line count |
| `RecordMakerApproval` | SYNC | Records first approver (maker) with timestamp |
| `RecordCheckerApproval` | SYNC | Records second approver (checker) with timestamp |
| `GenerateExportFile` | SYNC | Creates CSV/file format for GL system |
| `SendToGLSystem` | ASYNC_NEW_TX | Sends file to downstream GL system |
| `ArchiveBatch` | SYNC | Marks batch as archived for retention |

#### Missing Criteria for GLBatch

| Criterion Name | Purpose |
|----------------|---------|
| `GLBatchValidationCriterion` | Validates period is valid and not already processed |
| `GLBatchValidationFailedCriterion` | Inverse validation check |
| `ControlTotalsBalanced` | Ensures total_debits == total_credits |
| `MakerCheckerDifferentUsers` | Ensures maker and checker are different users |
| `GLAcknowledgmentReceived` | Checks if GL system has acknowledged receipt |

---

## Summary of Missing Components

### By Priority

#### CRITICAL (P0) - Blocking Spec Compliance
1. **GLBatch Workflow** - Completely missing
   - Create workflow file: `src/main/resources/workflow/gl_batch/version_1/GLBatch.json`
   - Implement 10 processors
   - Implement 5 criteria

#### HIGH (P1) - Incorrect Behavior
2. **Loan Workflow Corrections**
   - Fix reject transition (approval_pending -> draft)
   - Remove matured state
   - Fix settlement to be automatic
   - Remove settled -> closed transition
   - Add 2 processors: StampedApproval, GenerateReferenceSchedule
   - Add 2 criteria: LoanMaturityAndFullyPaidCriterion, SettlementQuoteAcceptedAndPaidCriterion

#### MEDIUM (P2) - Enhancements
3. **Party Workflow** - Consider whether update should go back to initial

---

## Next Steps

1. **Update LMS_SPECIFICATION.md** to document the validation error pattern
2. **Create GLBatch workflow** JSON file with all required processors/criteria
3. **Correct Loan workflow** JSON file
4. **Implement missing processors and criteria** (second step as requested)

---

## Validation Pattern Documentation for Spec

The following section should be added to LMS_SPECIFICATION.md before section 3.2:

### **3.1.5. Standard Validation Error Pattern**

All entities in the LMS implement a standard validation error pattern at the start of their lifecycle. This pattern ensures that invalid entities are caught early, validation errors are clearly communicated, and users have a clear path to correct and retry.

**Pattern Structure:**

Every entity workflow begins with an `initial` state that has two possible transitions:

1. **Success Path**: If the entity passes validation, it transitions to its first operational state (e.g., DRAFT for Loan, ACTIVE for Party, CAPTURED for Payment)
2. **Error Path**: If the entity fails validation, it transitions to a `validation_error` state where the error details are attached to the entity

**Components:**

- **{Entity}ValidationCriterion**: Returns `true` if the entity data is valid
- **{Entity}ValidationFailedCriterion**: Returns `true` if the entity data is invalid (inverse of validation criterion)
- **Attach{Entity}ValidationErrorProcessor**: Populates the `validationErrorReason` field with detailed error information
- **ClearValidationErrorReasonProcessor**: Clears the error field (shared across all entities)

**Recovery Mechanism:**

From the `validation_error` state, users can manually trigger a `FIX` transition that:
1. Clears the `validationErrorReason` field
2. Returns the entity to the `initial` state
3. Allows the user to correct the entity data and retry the validation

**Benefits:**

- **Audit Trail**: Every validation failure is recorded with detailed error information
- **User-Friendly**: Clear error messages guide users to fix issues
- **Data Quality**: Prevents invalid entities from entering the main lifecycle
- **Consistency**: Same pattern across all entities reduces cognitive load

**Example (Loan Entity):**

```
initial
  ├─> [NewLoanValidationCriterion = true] -> draft
  └─> [NewLoanValidationFailedCriterion = true] -> validation_error
        └─> [FIX (manual)] -> initial
```

This pattern must be preserved in all entity workflows.


