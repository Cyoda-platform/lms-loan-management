# Cyoda Database Index Analysis

## Executive Summary

This document analyzes all data retrieval patterns in the LMS codebase to recommend composite indexes for optimal query performance in Cyoda.

---

## Query Pattern Analysis by Entity

### 1. **Accrual Entity**

#### Query Pattern 1: Duplicate Detection
**Location**: `NotDuplicateAccrualCriterion.java`
**Fields**: `loanId` (EQUALS) + `asOfDate` (EQUALS)
**Frequency**: High - runs on every accrual creation
**Recommended Index**: `(loanId, asOfDate)`
**Rationale**: Exact match on both fields for duplicate detection

#### Query Pattern 2: Date Range Queries
**Location**: `GLAggregationService.java`
**Fields**: `asOfDate` (GREATER_THAN_OR_EQUAL + LESS_THAN_OR_EQUAL)
**Frequency**: Monthly - GL batch processing
**Recommended Index**: `(asOfDate)`
**Rationale**: Range queries on date for monthly aggregation

#### Query Pattern 3: Batch Cascade Queries
**Location**: `CascadeSettledCriterion.java`
**Fields**: `asOfDate` (GREATER_THAN_OR_EQUAL) + `runId` (EQUALS)
**Frequency**: Medium - EOD batch cascade operations
**Recommended Index**: `(runId, asOfDate)`
**Rationale**: Filter by batch first, then date range

#### Query Pattern 4: Batch Reconciliation
**Location**: `ProduceReconciliationReportProcessor.java`, `SpawnCascadeRecalcProcessor.java`
**Fields**: `runId` (EQUALS)
**Frequency**: High - every EOD batch
**Recommended Index**: `(runId)`
**Rationale**: Retrieve all accruals for a specific batch run

#### Query Pattern 5: Business ID Lookup
**Location**: `ReversePriorJournalsProcessor.java`
**Fields**: `accrualId` (EQUALS)
**Frequency**: Medium - when superseding accruals
**Recommended Index**: `(accrualId)` - unique
**Rationale**: Business identifier lookup

**Accrual Composite Index Recommendations**:
1. **PRIMARY**: `(loanId, asOfDate)` - covers duplicate detection
2. **SECONDARY**: `(runId, asOfDate)` - covers batch cascade queries
3. **TERTIARY**: `(accrualId)` - unique business ID
4. **QUATERNARY**: `(asOfDate)` - covers date range queries

---

### 2. **Loan Entity**

#### Query Pattern 1: Business ID Lookup
**Location**: Multiple files (LoanActiveOnDateCriterion, UpdateLoanAccruedInterestProcessor, CalculateAccrualAmountProcessor, etc.)
**Fields**: `loanId` (EQUALS)
**Frequency**: Very High - used throughout accrual processing, payment allocation
**Recommended Index**: `(loanId)` - unique
**Rationale**: Primary business identifier for loan lookups

**Files using this pattern**:
- `LoanActiveOnDateCriterion.java`
- `UpdateLoanAccruedInterestProcessor.java`
- `CalculateAccrualAmountProcessor.java`
- `RequiresRebookCriterion.java`
- `PaymentMatchesToLoanCriterion.java`
- `AllocatePaymentFunds.java`

**Loan Composite Index Recommendations**:
1. **PRIMARY**: `(loanId)` - unique business ID, extremely high usage

---

### 3. **Payment Entity**

#### Query Pattern 1: Loan Payment Lookup
**Location**: `SettlementQuoteAcceptedAndPaidCriterion.java`
**Fields**: `loanId` (EQUALS)
**Frequency**: Medium - settlement processing
**Recommended Index**: `(loanId)`
**Rationale**: Find all payments for a specific loan

**Payment Composite Index Recommendations**:
1. **PRIMARY**: `(loanId)` - find payments by loan

---

### 4. **SettlementQuote Entity**

#### Query Pattern 1: Accepted Quote Lookup
**Location**: `SettlementQuoteAcceptedAndPaidCriterion.java`
**Fields**: `loanId` (EQUALS) + `state` (EQUALS - lifecycle)
**Frequency**: Medium - settlement processing
**Recommended Index**: `(loanId, state)`
**Rationale**: Find accepted settlement quote for a loan

**SettlementQuote Composite Index Recommendations**:
1. **PRIMARY**: `(loanId, state)` - find quotes by loan and state

---

### 5. **Party Entity**

#### Query Pattern 1: Business ID Lookup
**Location**: `NewLoanValidationCriterion.java`
**Fields**: `partyId` (EQUALS)
**Frequency**: High - loan creation validation
**Recommended Index**: `(partyId)` - unique
**Rationale**: Validate party exists during loan creation

**Party Composite Index Recommendations**:
1. **PRIMARY**: `(partyId)` - unique business ID

---

### 6. **EODAccrualBatch Entity**

#### Query Pattern 1: Business ID Lookup
**Location**: Controller operations via `EntityCrudOperations`
**Fields**: Auto-generated UUID (no business ID)
**Frequency**: Low - batch management
**Recommended Index**: None needed beyond technical ID
**Rationale**: Uses technical UUID for lookups

---

### 7. **GLBatch Entity**

#### Query Pattern 1: Business ID Lookup
**Location**: Controller operations via `EntityCrudOperations`
**Fields**: Auto-generated UUID (no business ID)
**Frequency**: Low - GL batch management
**Recommended Index**: None needed beyond technical ID
**Rationale**: Uses technical UUID for lookups

---

## Lifecycle State Filtering Patterns

### Pattern: State-based Queries
**Location**: `EntityCrudOperations.java`, `SettlementQuoteAcceptedAndPaidCriterion.java`
**Fields**: `state` (lifecycle field) + entity-specific fields
**Frequency**: Medium - UI filtering, workflow transitions

**Recommendations**:
- For entities with frequent state filtering (SettlementQuote, Loan), consider composite indexes with state as the second field
- Example: `(loanId, state)` for SettlementQuote

---

## Generic Controller Query Patterns

### Pattern: Dynamic Field Filtering
**Location**: `EntityCrudOperations.java`
**Fields**: Variable - any entity field + optional state
**Frequency**: Medium - UI list/search operations

**Recommendations**:
- Cannot pre-index all possible combinations
- Rely on primary business ID indexes
- State filtering via LifecycleCondition

---

## Summary of Recommended Indexes

### High Priority (Critical Performance Impact)

| Entity | Index | Fields | Justification |
|--------|-------|--------|---------------|
| **Accrual** | IDX_ACCRUAL_LOAN_DATE | `(loanId, asOfDate)` | Duplicate detection - runs on every accrual creation |
| **Accrual** | IDX_ACCRUAL_RUN | `(runId, asOfDate)` | Batch processing - EOD operations |
| **Accrual** | IDX_ACCRUAL_BID | `(accrualId)` | Business ID lookup - unique |
| **Loan** | IDX_LOAN_BID | `(loanId)` | Business ID lookup - extremely high usage |
| **Party** | IDX_PARTY_BID | `(partyId)` | Business ID lookup - loan validation |

### Medium Priority (Performance Optimization)

| Entity | Index | Fields | Justification |
|--------|-------|--------|---------------|
| **Accrual** | IDX_ACCRUAL_DATE | `(asOfDate)` | Date range queries - monthly GL processing |
| **Payment** | IDX_PAYMENT_LOAN | `(loanId)` | Find payments by loan - settlement processing |
| **SettlementQuote** | IDX_QUOTE_LOAN_STATE | `(loanId, state)` | Find accepted quotes - settlement processing |

### Low Priority (Optional)

| Entity | Index | Fields | Justification |
|--------|-------|--------|---------------|
| **Loan** | IDX_LOAN_STATE | `(state)` | State-based filtering in UI |
| **Accrual** | IDX_ACCRUAL_STATE | `(state)` | State-based filtering in UI |

---

## Index Cardinality Analysis

### High Cardinality (Good for Indexing)
- `loanId` - unique per loan
- `accrualId` - unique per accrual
- `partyId` - unique per party
- `runId` - unique per batch run
- `asOfDate` - one value per day

### Low Cardinality (Less Effective Alone)
- `state` - limited number of workflow states (5-10 values)

**Recommendation**: Use low-cardinality fields as secondary fields in composite indexes, not as primary index fields.

---

## Query Performance Considerations

### 1. **Composite Index Field Order**
- Place equality conditions before range conditions
- Place high-cardinality fields before low-cardinality fields
- Example: `(runId, asOfDate)` not `(asOfDate, runId)` for batch cascade queries

### 2. **Index Coverage**
- Composite index `(loanId, asOfDate)` can serve queries on:
  - `loanId` alone
  - `loanId` + `asOfDate`
- But NOT `asOfDate` alone

### 3. **Lifecycle State Indexes**
- State is stored in metadata, not entity body
- LifecycleCondition queries filter on metadata.state
- Consider if Cyoda automatically indexes lifecycle state

---

## Implementation Notes

1. **Cyoda-Specific Considerations**:
   - Verify if Cyoda automatically indexes business ID fields
   - Check if lifecycle state fields are automatically indexed
   - Confirm if composite indexes are supported in Cyoda's data store

2. **Monitoring**:
   - Track query performance for high-frequency patterns
   - Monitor index usage and effectiveness
   - Adjust based on actual production query patterns

3. **Maintenance**:
   - Review index effectiveness quarterly
   - Remove unused indexes to reduce write overhead
   - Update indexes as query patterns evolve

---

## Next Steps

1. Validate with Cyoda documentation which indexes are automatically created
2. Implement high-priority indexes first
3. Measure query performance before/after index creation
4. Monitor production query patterns for 1-2 months
5. Adjust index strategy based on actual usage data

