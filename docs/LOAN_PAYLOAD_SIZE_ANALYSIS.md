# Loan Entity JSON Payload Size Analysis

## Executive Summary

Based on analysis of the LMS codebase, this document provides estimated JSON payload sizes for a maximally populated `Loan` entity and recommendations for Cyoda entitlement configuration.

## Entity Structure Analysis

### Main Entity: Loan
The `Loan` entity is the most complex entity in the system with **104 fields** across the main class and all nested classes.

### Maximal Loan Configuration

A fully populated commercial loan would include:

**Parties (8 total)**:
- 1 Borrower
- 5 Lenders
- 1 Administrative Agent  
- 1 Security Trustee

**Facilities (2 total)**:
- 1 Revolving Credit Facility
- 1 Term Loan Facility

**Per Facility**:
- Revolver: 2 tranches, 3 drawdowns, 2 repayments
- Term Loan: 3 tranches, 5 drawdowns, 4 repayments

**Per Tranche**:
- 3 fees (Commitment, Arrangement, Agency)
- 5 covenants (Net Leverage, Interest Coverage, Min Liquidity, Financial Statements, Compliance Certificate)
- 3 collateral items (Debenture, Share Pledge, Account Charge)
- Interest configuration with rate reset
- Amortization schedule
- Prepayment terms (voluntary + mandatory)

## Estimated JSON Payload Sizes

### Size Estimates (with indented JSON formatting)

Based on the structure above, estimated sizes are:

| Component | Count | Est. Size per Item | Subtotal |
|-----------|-------|-------------------|----------|
| **Main Loan Fields** | 18 | ~100 bytes | ~1.8 KB |
| **Parties** | 8 | ~200 bytes | ~1.6 KB |
| **Facilities** | 2 | ~500 bytes base | ~1.0 KB |
| **Tranches** | 5 total | ~300 bytes base | ~1.5 KB |
| **Interest Configs** | 5 | ~250 bytes | ~1.25 KB |
| **Fees** | 15 total (3×5) | ~150 bytes | ~2.25 KB |
| **Covenants** | 25 total (5×5) | ~250 bytes | ~6.25 KB |
| **Collateral** | 15 total (3×5) | ~150 bytes | ~2.25 KB |
| **Drawdowns** | 8 total | ~200 bytes | ~1.6 KB |
| **Repayments** | 6 total | ~250 bytes | ~1.5 KB |
| **Prepayment Terms** | 2 | ~300 bytes | ~0.6 KB |
| **Amortization** | 5 | ~150 bytes | ~0.75 KB |
| **JSON Overhead** | - | ~20% | ~4.5 KB |
| **TOTAL (Indented)** | - | - | **~27 KB** |

### Size Estimates (minified JSON)

Without indentation and whitespace:
- **Estimated Size: ~18-20 KB**

### Realistic Production Scenarios

| Scenario | Description | Est. Size |
|----------|-------------|-----------|
| **Simple Loan** | Single facility, 1 tranche, minimal covenants | 5-8 KB |
| **Standard Loan** | 1-2 facilities, 2-3 tranches, standard covenants | 12-15 KB |
| **Complex Syndicated Loan** | Multiple facilities, many tranches, full covenants | 20-30 KB |
| **Maximum Complexity** | As described above | 25-35 KB |

## Recommended Entitlement Configuration

### Conservative Configuration (Small Footprint)

```kotlin
val lmsEntitlements = mapOf(
    // Field limits
    Entitlement.NUM_MODEL_FIELDS to 110,  
    // Loan has 104 fields - allows small buffer
    
    Entitlement.NUM_MODEL_FIELDS_CUMULATIVE to 250,  
    // Current total: 222 fields across all 6 entities + 12% growth buffer
    
    // Model limits
    Entitlement.NUM_MODELS to 10,  
    // 6 current entities + 4 for future expansion
    
    // Payload size - CRITICAL FOR LOAN ENTITY
    Entitlement.PAYLOAD_SIZE to 5_242_880,  
    // 5 MB - Handles maximal loan (30 KB) with 150x safety margin
    // Allows for future growth and batch operations
    
    // Infrastructure (small footprint)
    Entitlement.NUM_CLIENT_NODES to 2,  
    // Minimal redundancy
    
    Entitlement.DISK_USAGE to 10_737_418_240,  
    // 10 GB for small deployment (hundreds to low thousands of loans)
    
    // API rate limits
    Entitlement.API_REQUEST to 10_000,  
    // Per hour - supports batch operations like EODAccrualBatch
    
    Entitlement.EXTERNALIZED_CALL to 5_000  
    // Per hour - for workflow processors and criteria
)
```

### Rationale for PAYLOAD_SIZE: 5 MB

1. **Maximal Loan Size**: ~30 KB (indented) or ~20 KB (minified)
2. **Safety Margin**: 5 MB provides 150-250x headroom
3. **Batch Operations**: Allows for potential batch payloads with multiple loans
4. **Future Growth**: Accommodates additional nested structures
5. **Metadata Overhead**: Cyoda adds metadata to payloads

### Alternative Configurations

#### Minimal Configuration (1 MB payload)
- Suitable if loans are guaranteed to be simple
- Risk: May fail on complex syndicated loans
- **Not Recommended** due to Loan entity complexity

#### Medium Configuration (10 MB payload)
- More comfortable margin
- Better for production environments
- Handles unexpected edge cases

#### Large Configuration (50 MB payload)
- Enterprise-grade
- Supports batch operations with many loans
- Recommended for production systems

## Field Count Summary

Total fields across all entity models: **222 fields**

Breakdown by entity:
- **Loan**: 104 fields (47% of total)
- **Party**: 18 fields
- **Payment**: 27 fields
- **SettlementQuote**: 25 fields
- **Accrual**: 17 fields
- **EODAccrualBatch**: 11 fields
- **Supporting Classes**: 20 fields

## Recommendations

1. **Start Conservative**: Use 5 MB PAYLOAD_SIZE for initial deployment
2. **Monitor Actual Sizes**: Track real-world payload sizes in production
3. **Plan for Growth**: The Loan entity dominates complexity - future enhancements will likely add fields here
4. **Test Edge Cases**: Create test loans with maximum complexity to validate limits
5. **Batch Considerations**: If batch operations send multiple loans in one payload, increase PAYLOAD_SIZE accordingly

## Test Implementation

Two test files have been created:

1. **LoanPayloadSizeTest.java** - JUnit test (requires working Gradle build)
2. **StandaloneLoanSizeTest.java** - Standalone Java application (can run independently)

Both create a maximally populated Loan entity and measure the JSON serialized size.

To run the standalone test (once Java version issues are resolved):
```bash
./gradlew test --tests "com.java_template.application.entity.loan.version_1.LoanPayloadSizeTest"
```

## Conclusion

For the LMS system with its complex Loan entity:

- **Minimum PAYLOAD_SIZE**: 1 MB (risky)
- **Recommended PAYLOAD_SIZE**: 5 MB (conservative, small footprint)
- **Comfortable PAYLOAD_SIZE**: 10 MB (production-ready)
- **Enterprise PAYLOAD_SIZE**: 50 MB (batch-capable)

The 5 MB recommendation provides a good balance between small footprint and operational safety, with 150-250x headroom over the estimated maximum loan size of 20-30 KB.

