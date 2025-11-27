# EOD Batch Accrual UI Requirements

**Date**: 2025-01-27  
**Version**: 1.0  
**Scope**: User interface requirements for EOD accrual batch operations

---

## 1. Overview

This document defines the user interface requirements for the EOD Accrual Batch system, enabling users to initiate and monitor daily interest accrual calculations for commercial loans.

### 1.1 Core Functionality
- Create and start EOD accrual batch runs
- Monitor batch execution progress
- View batch results and drill-down capabilities
- Support for TODAY and BACKDATED modes

### 1.2 User Roles
- **Finance Operations User**: Daily accrual operations
- **Finance Manager**: Backdated corrections and oversight
- **System Administrator**: Full access and troubleshooting

---

## 2. API Integration

### 2.1 Base Endpoint
**Base URL**: `/ui/eod-batches`

### 2.2 Create Batch Request
**Method**: `POST /ui/eod-batches`

**Request Structure**:
```json
{
  "batch": {
    "asOfDate": "YYYY-MM-DD",
    "mode": "TODAY|BACKDATED",
    "reasonCode": "string|null",
    "loanFilter": {
      "loanIds": ["uuid"],
      "productCodes": ["string"]
    }
  },
  "transitionRequest": {
    "name": "START"
  },
  "engineOptions": {
    "simulate": false,
    "maxSteps": 50
  }
}
```

### 2.3 Validation Request
Same as create but with `"simulate": true` and `"transitionRequest": null`

### 2.4 List Batches
**Method**: `GET /ui/eod-batches?page=0&size=20&state=COMPLETED&asOfDate=2025-10-07`

### 2.5 Get Batch Details
**Method**: `GET /ui/eod-batches/{id}`

---

## 3. UI Components

### 3.1 EOD Batch Panel

#### Layout
```
┌─────────────────────────────────────────────────────────────┐
│ EOD Accrual Batch                                           │
├─────────────────────────────────────────────────────────────┤
│ [Create New Batch]                                          │
│                                                             │
│ Recent Batches:                                             │
│ ┌───────────────────────────────────────────────────────┐  │
│ │ Date       | Mode      | State     | Loans           │  │
│ │ 2025-01-27 | TODAY     | COMPLETED | 1,234 loans     │  │
│ │ 2025-01-26 | TODAY     | COMPLETED | 1,230 loans     │  │
│ │ 2025-01-15 | BACKDATED | COMPLETED | 45 loans (PPA)  │  │
│ └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### Recent Batches Table
**Columns**:
- Date (asOfDate)
- Mode (TODAY/BACKDATED with PPA indicator)
- State (color-coded badges)
- Loan Count (from metrics.eligibleLoans)
- Actions (View Details button)

**Sorting**: Default by asOfDate descending
**Pagination**: 10 items per page
**Refresh**: Auto-refresh every 30 seconds for active batches

### 3.2 Create New Batch Form

#### Form Fields

| Field | Type | Required | Validation | Default |
|-------|------|----------|------------|---------|
| **As Of Date** | Date picker | Yes | Must be business day | Today |
| **Mode** | Radio buttons | Yes | TODAY \| BACKDATED | TODAY |
| **Reason Code** | Dropdown | Conditional | Required if BACKDATED | null |
| **Loan Filter** | Multi-select | No | Valid loan IDs | Empty |

#### Reason Code Options
- `DATA_CORRECTION` - "Data Correction"
- `RATE_ADJUSTMENT` - "Rate Adjustment"
- `SYSTEM_ERROR` - "System Error"
- `OTHER` - "Other"

#### Form Behavior
- **Mode = TODAY**: Hide reason code field
- **Mode = BACKDATED**:
    - Show reason code field (required)
    - Display warning: "Back-dated runs may create Prior Period Adjustments (PPAs)"
    - Check `backdated_eod_execute` permission

#### Form Actions

| Button | Behavior | API Call |
|--------|----------|----------|
| **Validate** | Show eligibility preview | POST with `simulate: true` |
| **Start Run** | Create and start batch | POST with `transitionRequest.name: "START"` |
| **Cancel** | Close form | None |

#### Validation Response Display
```
✓ Eligible Loans: 1,234
✓ Business Day: Valid
✓ No Active Batch for Date
⚠ Period Status: CLOSED (PPAs will be flagged)
```

### 3.3 Batch Detail View

#### Header Section
- Batch ID
- As Of Date
- Mode (with PPA warning if applicable)
- Current State (with progress indicator)
- Initiated By
- Reason Code (if backdated)

#### Progress Section
- State progression visual
- Progress bar (processedLoans / eligibleLoans)
- Real-time metrics:
    - Eligible Loans
    - Processed Loans
    - Accruals Created
    - Total Debited/Credited

#### Tabs

**Tab 1: Accruals**
- List all accruals created by batch
- Columns: Loan ID, As Of Date, Interest Amount, State, PPA Flag, Posted At
- Filters: State dropdown, PPA checkbox
- Pagination: 50 items per page
- Actions: View accrual details

**Tab 2: Failures** (if any)
- List failed accruals with error messages
- Columns: Loan ID, Error Code, Error Message, Retry Action
- Actions: Retry individual failures

**Tab 3: Summary**
- Aggregate metrics
- Journal entry totals
- Reconciliation status

---

## 4. User Experience Requirements

### 4.1 Performance
- Form validation: < 500ms response time
- Batch creation: < 2 seconds response time
- Progress updates: Real-time via polling (30-second intervals)
- Page load: < 3 seconds

### 4.2 Accessibility
- WCAG 2.1 AA compliance
- Keyboard navigation support
- Screen reader compatibility
- High contrast mode support

### 4.3 Responsive Design
- Mobile-friendly (768px+ width)
- Tablet optimization
- Desktop primary target

### 4.4 Error Handling

#### Client-Side Validation
- Required field indicators
- Real-time validation feedback
- Clear error messages

#### Server-Side Error Responses

| HTTP Code | Scenario | UI Action |
|-----------|----------|-----------|
| 400 | Bad Request | Show field-specific errors |
| 403 | Permission Denied | Show permission error dialog |
| 409 | Active Batch Exists | Show conflict message with link to existing batch |
| 422 | Business Validation | Show validation errors with suggestions |
| 500 | Server Error | Show generic error with retry option |

### 4.5 Security
- Permission-based feature visibility
- CSRF protection on all forms
- Input sanitization
- Audit logging for all actions

---

## 5. Technical Requirements

### 5.1 Framework Integration
- Spring Boot REST API integration
- JSON request/response handling
- Error response parsing
- Authentication token management

### 5.2 State Management
- Form state persistence during validation
- Batch status caching
- Real-time updates via polling or WebSocket

### 5.3 Data Validation
- Client-side validation matching server rules
- Business day calendar integration
- Permission checking before form display

### 5.4 Navigation
- Breadcrumb navigation
- Deep linking to batch details
- Browser back/forward support

---

## 6. Acceptance Criteria

### 6.1 Create Batch Flow
- [ ] User can access create batch form
- [ ] Form validates required fields client-side
- [ ] Mode selection shows/hides reason code appropriately
- [ ] Validate button shows eligibility preview
- [ ] Start button creates batch and redirects to detail view
- [ ] Permission checks prevent unauthorized access

### 6.2 Batch Monitoring
- [ ] Recent batches list shows current status
- [ ] Batch detail view updates in real-time
- [ ] Progress indicators reflect actual completion
- [ ] Error states are clearly communicated

### 6.3 Error Handling
- [ ] All error scenarios display appropriate messages
- [ ] Users can recover from validation errors
- [ ] Network errors show retry options
- [ ] Permission errors provide clear guidance

### 6.4 Performance
- [ ] All API calls complete within specified timeouts
- [ ] UI remains responsive during batch processing
- [ ] Large batch results paginate properly
- [ ] Real-time updates don't impact performance

---

## 7. Future Enhancements

### 7.1 Phase 2 Features
- Batch scheduling and automation
- Email notifications for completion
- Advanced filtering and search
- Bulk operations on multiple batches

### 7.2 Reporting Integration
- Export batch results to CSV/Excel
- Integration with reporting dashboard
- Historical trend analysis
- Performance metrics tracking