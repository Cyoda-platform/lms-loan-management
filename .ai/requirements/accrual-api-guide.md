# Accrual API Guide for UI Development

## Overview

This guide explains how to retrieve accruals from the LMS system, particularly in the context of EOD (End-of-Day) accrual batch processing.

## Key Concepts

### Entity Relationships

- **EODAccrualBatch**: Orchestrates the daily accrual run for multiple loans
  - Has a `batchId` (business identifier)
  - Creates multiple `Accrual` entities during processing
  
- **Accrual**: Represents daily interest computation for a single loan
  - Has an `accrualId` (business identifier)
  - Has a `runId` field that references the `batchId` of the batch that created it
  - Has a `loanId` field that references the loan

### Important Distinction

- **Technical UUID** (`meta.id`): Internal Cyoda system identifier (Type 1 UUID)
- **Business ID**: Human-readable identifier used in business logic
  - For EODAccrualBatch: `batchId`
  - For Accrual: `accrualId`

## API Endpoints

### 1. Get All Accruals for a Batch

To retrieve all accruals created by a specific EOD batch, use the **query parameter** `runId`:

```http
GET /ui/accruals?runId={batchId}
```

**Example:**
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals?runId=f9ad0131-a6c1-4ad5-90f1-95ebe67ef407' \
  -H 'accept: */*'
```

**Response:**
```json
{
  "content": [
    {
      "entity": {
        "accrualId": "ca17af11-a951-482d-9eff-4d83f357ac5d",
        "loanId": "LOAN-SEED-0003",
        "asOfDate": "2025-02-04",
        "runId": "f9ad0131-a6c1-4ad5-90f1-95ebe67ef407",
        "interestAmount": 29.63,
        ...
      },
      "meta": {
        "id": "305b586e-b6ca-11b2-aedb-7a2c7d0112fa",
        "state": "POSTED",
        ...
      }
    },
    ...
  ],
  "pageable": { ... },
  "totalElements": 17
}
```

### 2. Get Single Accrual by Business ID

To retrieve a specific accrual by its business identifier (`accrualId`):

```http
GET /ui/accruals/business/{accrualId}
```

**Example:**
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals/business/ca17af11-a951-482d-9eff-4d83f357ac5d' \
  -H 'accept: */*'
```

### 3. Get Single Accrual by Technical UUID

To retrieve a specific accrual by its internal technical ID:

```http
GET /ui/accruals/{technicalId}
```

**Example:**
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals/305b586e-b6ca-11b2-aedb-7a2c7d0112fa' \
  -H 'accept: */*'
```

**Note:** This requires the `meta.id` value, not the `accrualId`.

### 4. List Accruals with Filters

The list endpoint supports multiple filters:

```http
GET /ui/accruals?page={page}&size={size}&state={state}&loanId={loanId}&asOfDate={asOfDate}&runId={runId}
```

**Query Parameters:**
- `runId` - Filter by batch ID (the EODAccrualBatch that created the accruals)
- `loanId` - Filter by specific loan
- `asOfDate` - Filter by accrual date (format: YYYY-MM-DD)
- `state` - Filter by workflow state (e.g., POSTED, CALCULATED, ELIGIBLE)
- `page` - Page number for pagination (default: 0)
- `size` - Page size (default: 20)
- `pointInTime` - Optional temporal query for historical data (ISO 8601 format)

**Examples:**

Get all accruals for a specific loan:
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals?loanId=LOAN-SEED-0003' \
  -H 'accept: */*'
```

Get all posted accruals for a specific date:
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals?asOfDate=2025-02-04&state=POSTED' \
  -H 'accept: */*'
```

Get all accruals from a batch with pagination:
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals?runId=f9ad0131-a6c1-4ad5-90f1-95ebe67ef407&page=0&size=50' \
  -H 'accept: */*'
```

Combine multiple filters:
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/accruals?runId=f9ad0131-a6c1-4ad5-90f1-95ebe67ef407&state=POSTED&loanId=LOAN-SEED-0003' \
  -H 'accept: */*'
```

## EOD Batch Endpoints

### Get Batch by Business ID

```http
GET /ui/eod-batches/business/{batchId}
```

**Example:**
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/eod-batches/business/f9ad0131-a6c1-4ad5-90f1-95ebe67ef407' \
  -H 'accept: */*'
```

### List All Batches

```http
GET /ui/eod-batches?page={page}&size={size}&state={state}&asOfDate={asOfDate}&mode={mode}
```

**Example:**
```bash
curl -X 'GET' \
  'http://localhost:8080/ui/eod-batches?page=0&size=20' \
  -H 'accept: */*'
```

## Common Workflow: Display Batch Results

To display all accruals for a completed batch:

1. **Get the batch details:**
   ```
   GET /ui/eod-batches/business/{batchId}
   ```
   
2. **Get all accruals from that batch:**
   ```
   GET /ui/accruals?runId={batchId}&page=0&size=100
   ```

3. **Optional: Filter by state to show only posted accruals:**
   ```
   GET /ui/accruals?runId={batchId}&state=POSTED&page=0&size=100
   ```

## Error Handling

### Common Errors

**400 Bad Request - Invalid UUID Format:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to retrieve Accrual with ID 'f9ad0131-a6c1-4ad5-90f1-95ebe67ef407': ... is not a Type 1 (time-based) UUID"
}
```

**Cause:** Using a business ID (like `batchId` or `accrualId`) in a path parameter that expects a technical UUID.

**Solution:** Use the `/business/{businessId}` endpoint instead, or use the query parameter for filtering.

**404 Not Found:**
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Accrual with ID 'invalid-id' not found"
}
```

**Cause:** The entity with the specified ID does not exist.

## Response Structure

All entity responses follow this structure:

```json
{
  "entity": {
    // Business data (Accrual fields)
    "accrualId": "...",
    "loanId": "...",
    "runId": "...",
    ...
  },
  "meta": {
    // System metadata
    "id": "...",           // Technical UUID
    "state": "...",        // Workflow state
    "creationDate": "...",
    "lastUpdateTime": "..."
  }
}
```

List responses are paginated:

```json
{
  "content": [ /* array of entity-with-metadata */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    ...
  },
  "totalElements": 17,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

## Quick Reference

| Use Case | Endpoint | Example |
|----------|----------|---------|
| Get all accruals from a batch | `GET /ui/accruals?runId={batchId}` | `/ui/accruals?runId=f9ad0131-a6c1-4ad5-90f1-95ebe67ef407` |
| Get accrual by business ID | `GET /ui/accruals/business/{accrualId}` | `/ui/accruals/business/ca17af11-a951-482d-9eff-4d83f357ac5d` |
| Get all accruals for a loan | `GET /ui/accruals?loanId={loanId}` | `/ui/accruals?loanId=LOAN-SEED-0003` |
| Get batch by business ID | `GET /ui/eod-batches/business/{batchId}` | `/ui/eod-batches/business/f9ad0131-a6c1-4ad5-90f1-95ebe67ef407` |
| List all batches | `GET /ui/eod-batches` | `/ui/eod-batches?page=0&size=20` |

## Notes

- Always use **query parameters** (e.g., `?runId=...`) to filter by business identifiers
- Use **path parameters** with `/business/{id}` to retrieve a single entity by business ID
- Use **path parameters** with `/{id}` to retrieve a single entity by technical UUID
- The `runId` field in Accrual entities stores the `batchId` of the EODAccrualBatch that created them
- Pagination is supported on all list endpoints (default: page=0, size=20)

