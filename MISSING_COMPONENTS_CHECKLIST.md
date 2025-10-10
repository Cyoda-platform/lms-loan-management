# Missing Components Checklist

Paul Muadib,

Quick reference checklist for implementing missing processors and criteria.

## Loan Entity

### Processors to Implement

- [ ] **StampedApproval** (SYNC)
  - Package: `com.java_template.application.processor.loan`
  - Implements: `CyodaProcessor`
  - Supports: `Loan` entity, transition `approve_loan`
  - Logic: Record approver user ID, timestamp, role in audit metadata

- [ ] **GenerateReferenceSchedule** (ASYNC_NEW_TX)
  - Package: `com.java_template.application.processor.loan`
  - Implements: `CyodaProcessor`
  - Supports: `Loan` entity, transition `fund_loan`
  - Logic: Generate amortization schedule based on term, APR, principal

### Criteria to Implement

- [ ] **LoanMaturityAndFullyPaidCriterion**
  - Package: `com.java_template.application.criterion.loan`
  - Implements: `CyodaCriterion`
  - Supports: `Loan` entity, transition `close_loan`
  - Logic: `maturityDate <= LocalDate.now() && outstandingPrincipal.compareTo(BigDecimal.ZERO) == 0`

- [ ] **SettlementQuoteAcceptedAndPaidCriterion**
  - Package: `com.java_template.application.criterion.loan`
  - Implements: `CyodaCriterion`
  - Supports: `Loan` entity, transition `settle_loan`
  - Logic: Check if accepted SettlementQuote exists and payment received

---

## GLBatch Entity

### Entity Class to Create (if not exists)

- [ ] **GLBatch**
  - Package: `com.java_template.application.entity`
  - Implements: `CyodaEntity`
  - Fields: period, status, exportFormat, controlTotals, glLines (List<GLLine>), approvals, etc.
  - **Important**: GLLine is a simple POJO/data class embedded within GLBatch, NOT a separate CyodaEntity
  - GLLine structure: glLineId, glAccount, description, type (DEBIT/CREDIT), amount

### Processors to Implement

- [ ] **GLBatchValidationCriterion**
  - Package: `com.java_template.application.criterion.glbatch`
  - Implements: `CyodaCriterion`
  - Logic: Validate period not already processed, valid date range

- [ ] **GLBatchValidationFailedCriterion**
  - Package: `com.java_template.application.criterion.glbatch`
  - Implements: `CyodaCriterion`
  - Logic: Inverse of GLBatchValidationCriterion

- [ ] **AttachGLBatchValidationErrorProcessor** (SYNC)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Populate validationErrorReason field

- [ ] **SummarizePeriod** (ASYNC_NEW_TX)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Query all Accruals and Payments for period, aggregate by GL account, create GLLines

- [ ] **CalculateControlTotals** (SYNC)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Sum all debits, sum all credits, count lines, ensure balanced

- [ ] **RecordMakerApproval** (SYNC)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Record first approver (maker) user ID and timestamp

- [ ] **RecordCheckerApproval** (SYNC)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Record second approver (checker) user ID and timestamp

- [ ] **GenerateExportFile** (SYNC)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Format GLLines as CSV or required GL system format

- [ ] **SendToGLSystem** (ASYNC_NEW_TX)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Send file to GL system via API or file transfer

- [ ] **ArchiveBatch** (SYNC)
  - Package: `com.java_template.application.processor.glbatch`
  - Implements: `CyodaProcessor`
  - Logic: Mark batch as archived, update retention metadata

### Criteria to Implement

- [ ] **ControlTotalsBalanced**
  - Package: `com.java_template.application.criterion.glbatch`
  - Implements: `CyodaCriterion`
  - Logic: `totalDebits.compareTo(totalCredits) == 0`

- [ ] **MakerCheckerDifferentUsers**
  - Package: `com.java_template.application.criterion.glbatch`
  - Implements: `CyodaCriterion`
  - Logic: `!makerUserId.equals(checkerUserId)`

- [ ] **GLAcknowledgmentReceived**
  - Package: `com.java_template.application.criterion.glbatch`
  - Implements: `CyodaCriterion`
  - Logic: Check if GL system acknowledgment received (poll or event-based)

---

## Workflow Deployment

### Files to Deploy

- [ ] Replace `src/main/resources/workflow/loan/version_1/Loan.json` with `Loan_CORRECTED.json`
- [ ] Deploy `src/main/resources/workflow/gl_batch/version_1/GLBatch.json`

### Import Commands

```bash
# After implementing processors/criteria, import workflows:

# Import corrected Loan workflow
./gradlew run --args="import-workflow loan 1 src/main/resources/workflow/loan/version_1/Loan_CORRECTED.json REPLACE"

# Import new GLBatch workflow
./gradlew run --args="import-workflow gl_batch 1 src/main/resources/workflow/gl_batch/version_1/GLBatch.json REPLACE"
```

---

## Testing Checklist

### Loan Workflow Tests

- [ ] Test validation error pattern (invalid loan -> validation_error -> FIX -> retry)
- [ ] Test reject returns to draft (not rejected state)
- [ ] Test approval records maker metadata (StampedApproval)
- [ ] Test funding generates schedule (GenerateReferenceSchedule)
- [ ] Test maturity closure only when fully paid (LoanMaturityAndFullyPaidCriterion)
- [ ] Test automatic settlement when quote accepted (SettlementQuoteAcceptedAndPaidCriterion)
- [ ] Test settled and closed are terminal states

### GLBatch Workflow Tests

- [ ] Test validation error pattern (invalid batch -> validation_error -> FIX -> retry)
- [ ] Test period summarization (SummarizePeriod aggregates correctly)
- [ ] Test control totals balanced (debits == credits)
- [ ] Test maker/checker different users enforcement
- [ ] Test export file generation
- [ ] Test GL system integration
- [ ] Test acknowledgment receipt
- [ ] Test archival

---

## Implementation Order

1. **Phase 1: Loan Corrections** (1-2 days)
   - Implement 2 Loan processors
   - Implement 2 Loan criteria
   - Deploy corrected Loan workflow
   - Test Loan workflow

2. **Phase 2: GLBatch Foundation** (2-3 days)
   - Create GLBatch entity class
   - Implement validation processors/criteria
   - Implement SummarizePeriod processor
   - Implement CalculateControlTotals processor
   - Test basic batch creation and preparation

3. **Phase 3: GLBatch Approval & Export** (2-3 days)
   - Implement maker/checker processors
   - Implement maker/checker criteria
   - Implement export processors
   - Test approval workflow

4. **Phase 4: GLBatch Integration** (1-2 days)
   - Implement GL system integration
   - Implement acknowledgment handling
   - Implement archival
   - End-to-end testing

**Total Estimated Effort: 6-10 days**

---

## Reference Documents

- **WORKFLOW_ANALYSIS.md** - Detailed analysis with rationale
- **WORKFLOW_CORRECTIONS_SUMMARY.md** - Executive summary
- **LMS_SPECIFICATION.md** - Updated specification with validation pattern
- **llm_example/** - Code examples and patterns

---

## Notes

- All processors must implement `CyodaProcessor` interface
- All criteria must implement `CyodaCriterion` interface
- Use `ProcessorSerializer` for type-safe entity processing in processors
- Use `CriterionSerializer` for evaluation logic in criteria
- Criteria must be pure functions (no side effects)
- Processors can use entityService to read/update OTHER entities, not current entity
- Follow existing patterns in `llm_example/` directory

Ready to proceed when you are.

