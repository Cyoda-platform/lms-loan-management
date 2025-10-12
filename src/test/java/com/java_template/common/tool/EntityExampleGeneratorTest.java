package com.java_template.common.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.application.entity.accrual.version_1.*;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.party.version_1.Party;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class that generates example JSON files for each CyodaEntity implementation.
 * 
 * This test creates valid example instances of each entity and serializes them to JSON files
 * in the src/main/resources/entity-schemas/examples/ directory.
 * 
 * By generating examples from actual entity instances, we guarantee that:
 * 1. Examples are always valid and up-to-date with the entity classes
 * 2. All required fields are present
 * 3. Data types match exactly
 * 4. Nested objects are properly structured
 */
@DisplayName("Entity Example Generator Tests")
class EntityExampleGeneratorTest {

    private ObjectMapper objectMapper;
    private Path examplesBaseDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Set base directory for examples
        examplesBaseDir = Paths.get("src/main/resources/entity-schemas/examples");
    }

    @Test
    @DisplayName("Generate Accrual example")
    void generateAccrualExample() throws Exception {
        // Given - Create a valid Accrual instance
        Accrual accrual = new Accrual();
        accrual.setAccrualId("ACC-2024-001-20240115");
        accrual.setLoanId("LOAN-2024-001");
        accrual.setAsOfDate(LocalDate.of(2024, 1, 15));
        accrual.setCurrency("GBP");
        accrual.setAprId("APR-001");
        accrual.setDayCountConvention(DayCountConvention.ACT_365);
        accrual.setDayCountFraction(new BigDecimal("0.00273972602739726"));
        
        PrincipalSnapshot snapshot = new PrincipalSnapshot();
        snapshot.setAmount(new BigDecimal("1000000.00"));
        snapshot.setEffectiveAtStartOfDay(true);
        accrual.setPrincipalSnapshot(snapshot);
        
        accrual.setInterestAmount(new BigDecimal("157.53"));
        accrual.setPostingTimestamp(OffsetDateTime.of(2024, 1, 16, 2, 0, 0, 0, ZoneOffset.UTC));
        accrual.setPriorPeriodFlag(false);
        accrual.setRunId("BATCH-2024-01-15");
        accrual.setVersion(1);
        
        List<JournalEntry> entries = new ArrayList<>();
        
        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setEntryId("JE-001");
        debitEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        debitEntry.setDirection(JournalEntryDirection.DR);
        debitEntry.setAmount(new BigDecimal("157.53"));
        debitEntry.setKind(JournalEntryKind.ORIGINAL);
        debitEntry.setMemo("Daily interest accrual");
        entries.add(debitEntry);
        
        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setEntryId("JE-002");
        creditEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        creditEntry.setDirection(JournalEntryDirection.CR);
        creditEntry.setAmount(new BigDecimal("157.53"));
        creditEntry.setKind(JournalEntryKind.ORIGINAL);
        creditEntry.setMemo("Daily interest accrual");
        entries.add(creditEntry);
        
        accrual.setJournalEntries(entries);
        
        // When - Write to file
        writeExampleToFile("Accrual", "example-accrual.json", accrual);
        
        // Then - Verify file was created and is valid JSON
        Path exampleFile = examplesBaseDir.resolve("Accrual/example-accrual.json");
        assertTrue(Files.exists(exampleFile), "Example file should be created");
        
        // Verify we can read it back
        Accrual readBack = objectMapper.readValue(exampleFile.toFile(), Accrual.class);
        assertNotNull(readBack);
        assertEquals(accrual.getLoanId(), readBack.getLoanId());
    }

    @Test
    @DisplayName("Generate EODAccrualBatch example")
    void generateEODAccrualBatchExample() throws Exception {
        // Given
        EODAccrualBatch batch = new EODAccrualBatch();
        batch.setBatchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        batch.setAsOfDate(LocalDate.of(2024, 1, 15));
        batch.setMode(BatchMode.TODAY);
        batch.setInitiatedBy("system-scheduler");
        batch.setPeriodStatus(PeriodStatus.OPEN);
        
        BatchMetrics metrics = new BatchMetrics();
        metrics.setEligibleLoans(150);
        metrics.setProcessedLoans(150);
        metrics.setAccrualsCreated(150);
        metrics.setPostings(300);
        metrics.setDebited(new BigDecimal("23629.50"));
        metrics.setCredited(new BigDecimal("23629.50"));
        metrics.setImbalances(0);
        batch.setMetrics(metrics);
        
        batch.setReportId(UUID.fromString("650e8400-e29b-41d4-a716-446655440001"));
        
        // When
        writeExampleToFile("EODAccrualBatch", "example-batch.json", batch);
        
        // Then
        Path exampleFile = examplesBaseDir.resolve("EODAccrualBatch/example-batch.json");
        assertTrue(Files.exists(exampleFile));
        
        EODAccrualBatch readBack = objectMapper.readValue(exampleFile.toFile(), EODAccrualBatch.class);
        assertNotNull(readBack);
        assertEquals(batch.getAsOfDate(), readBack.getAsOfDate());
    }

    @Test
    @DisplayName("Generate GLBatch example")
    void generateGLBatchExample() throws Exception {
        // Given
        GLBatch batch = new GLBatch();
        batch.setBatchId("GL-BATCH-2024-01");
        batch.setPeriod("2024-01");
        batch.setExportFormat("CSV");
        batch.setStatus("PREPARED");
        
        GLBatch.ControlTotals controlTotals = new GLBatch.ControlTotals();
        controlTotals.setTotalDebits(new BigDecimal("95000.00"));
        controlTotals.setTotalCredits(new BigDecimal("95000.00"));
        controlTotals.setLineCount(2);
        controlTotals.setIsBalanced(true);
        batch.setControlTotals(controlTotals);
        
        List<GLBatch.GLLine> glLines = new ArrayList<>();
        
        GLBatch.GLLine debitLine = new GLBatch.GLLine();
        debitLine.setGlLineId("GL-LINE-001");
        debitLine.setGlAccount("1100-Interest-Receivable");
        debitLine.setDescription("January 2024 interest accrual");
        debitLine.setType("DEBIT");
        debitLine.setAmount(new BigDecimal("47500.00"));
        debitLine.setCurrency("GBP");
        debitLine.setCostCenter("CC-001");
        debitLine.setProduct("Commercial Loans");
        glLines.add(debitLine);
        
        GLBatch.GLLine creditLine = new GLBatch.GLLine();
        creditLine.setGlLineId("GL-LINE-002");
        creditLine.setGlAccount("4100-Interest-Income");
        creditLine.setDescription("January 2024 interest accrual");
        creditLine.setType("CREDIT");
        creditLine.setAmount(new BigDecimal("47500.00"));
        creditLine.setCurrency("GBP");
        creditLine.setCostCenter("CC-001");
        creditLine.setProduct("Commercial Loans");
        glLines.add(creditLine);
        
        batch.setGlLines(glLines);
        
        GLBatch.Approvals approvals = new GLBatch.Approvals();
        approvals.setMakerUserId("finance-user-001");
        approvals.setMakerApprovedAt(LocalDateTime.of(2024, 2, 1, 9, 0, 0));
        approvals.setMakerRole("Finance Analyst");
        batch.setApprovals(approvals);
        
        // When
        writeExampleToFile("GLBatch", "example-gl-batch.json", batch);
        
        // Then
        Path exampleFile = examplesBaseDir.resolve("GLBatch/example-gl-batch.json");
        assertTrue(Files.exists(exampleFile));
        
        GLBatch readBack = objectMapper.readValue(exampleFile.toFile(), GLBatch.class);
        assertNotNull(readBack);
        assertEquals(batch.getBatchId(), readBack.getBatchId());
    }

    @Test
    @DisplayName("Generate Loan simple example")
    void generateLoanSimpleExample() throws Exception {
        // Given
        Loan loan = new Loan();
        loan.setLoanId("LOAN-2024-001");
        loan.setAgreementId("AGR-2024-001");
        loan.setPartyId("PARTY-001");
        loan.setPrincipalAmount(new BigDecimal("1000000.00"));
        loan.setApr(new BigDecimal("5.75"));
        loan.setTermMonths(36);
        loan.setFundingDate(LocalDate.of(2024, 1, 15));
        loan.setMaturityDate(LocalDate.of(2027, 1, 15));
        loan.setOutstandingPrincipal(new BigDecimal("1000000.00"));
        loan.setAccruedInterest(new BigDecimal("0.00"));
        loan.setPurpose("General corporate purposes");
        loan.setGoverningLaw("England and Wales");
        loan.setDayCountBasis("ACT/365");
        loan.setCurrency("GBP");
        
        // When
        writeExampleToFile("Loan", "example-simple-loan.json", loan);
        
        // Then
        Path exampleFile = examplesBaseDir.resolve("Loan/example-simple-loan.json");
        assertTrue(Files.exists(exampleFile));
        
        Loan readBack = objectMapper.readValue(exampleFile.toFile(), Loan.class);
        assertNotNull(readBack);
        assertEquals(loan.getLoanId(), readBack.getLoanId());
    }

    @Test
    @DisplayName("Generate Loan complex example")
    void generateLoanComplexExample() throws Exception {
        // Given
        Loan loan = new Loan();
        loan.setLoanId("LOAN-2024-002");
        loan.setAgreementId("AGR-2024-002");
        loan.setPartyId("PARTY-002");
        loan.setPrincipalAmount(new BigDecimal("5000000.00"));
        loan.setApr(new BigDecimal("6.25"));
        loan.setTermMonths(60);
        loan.setFundingDate(LocalDate.of(2024, 3, 1));
        loan.setMaturityDate(LocalDate.of(2029, 3, 1));
        loan.setOutstandingPrincipal(new BigDecimal("5000000.00"));
        loan.setAccruedInterest(new BigDecimal("0.00"));
        loan.setPurpose("Acquisition financing");
        loan.setGoverningLaw("England and Wales");
        loan.setDayCountBasis("ACT/365");
        loan.setCurrency("GBP");

        List<Loan.LoanParty> parties = new ArrayList<>();

        Loan.LoanParty borrower = new Loan.LoanParty();
        borrower.setPartyId("PARTY-002");
        borrower.setName("TechCorp Industries Ltd");
        borrower.setLei("213800TECHCORP123456");
        borrower.setRole("Borrower");
        borrower.setJurisdiction("England and Wales");
        borrower.setCommitmentAmount(new BigDecimal("5000000.00"));
        borrower.setCommitmentCurrency("GBP");
        parties.add(borrower);

        Loan.LoanParty lender = new Loan.LoanParty();
        lender.setPartyId("PARTY-LENDER-001");
        lender.setName("Global Bank plc");
        lender.setLei("213800GLOBALBANK12345");
        lender.setRole("Lender");
        lender.setJurisdiction("England and Wales");
        lender.setCommitmentAmount(new BigDecimal("5000000.00"));
        lender.setCommitmentCurrency("GBP");
        parties.add(lender);

        loan.setParties(parties);

        List<Loan.LoanFacility> facilities = new ArrayList<>();
        Loan.LoanFacility facility = new Loan.LoanFacility();
        facility.setFacilityId("FAC-001");
        facility.setType("Term Loan");
        facility.setCurrency("GBP");
        facility.setLimit(new BigDecimal("5000000.00"));

        Loan.LoanAvailability availability = new Loan.LoanAvailability();
        availability.setStartDate(LocalDate.of(2024, 3, 1));
        availability.setEndDate(LocalDate.of(2024, 6, 1));
        availability.setConditionsPrecedent(List.of(
            "Executed loan agreement",
            "Security documentation in place",
            "No material adverse change"
        ));
        facility.setAvailability(availability);

        List<Loan.LoanTranche> tranches = new ArrayList<>();
        Loan.LoanTranche tranche = new Loan.LoanTranche();
        tranche.setTrancheId("TRANCHE-A");
        tranche.setLimit(new BigDecimal("5000000.00"));
        tranche.setPurpose("Acquisition financing");

        Loan.LoanInterest interest = new Loan.LoanInterest();
        interest.setIndex("SONIA");
        interest.setTenor("3M");
        interest.setSpreadBps(275);
        interest.setFloorRate(new BigDecimal("0.00"));
        interest.setDayCount("ACT/365F");
        interest.setCompounding("Compound");

        Loan.LoanRateReset rateReset = new Loan.LoanRateReset();
        rateReset.setFrequency("Quarterly");
        rateReset.setBusinessDayConvention("ModifiedFollowing");
        interest.setRateReset(rateReset);
        tranche.setInterest(interest);

        List<Loan.LoanFee> fees = new ArrayList<>();
        Loan.LoanFee fee = new Loan.LoanFee();
        fee.setFeeId("FEE-001");
        fee.setType("Arrangement");
        fee.setBasis("Upfront");
        fee.setAmount(new BigDecimal("50000.00"));
        fee.setPayOn("Signing");
        fees.add(fee);
        tranche.setFees(fees);

        Loan.LoanAmortization amortization = new Loan.LoanAmortization();
        amortization.setType("Bullet");
        amortization.setSchedule(new ArrayList<>());
        tranche.setAmortization(amortization);

        tranches.add(tranche);
        facility.setTranches(tranches);
        facilities.add(facility);
        loan.setFacilities(facilities);

        // When
        writeExampleToFile("Loan", "example-complex-loan.json", loan);

        // Then
        Path exampleFile = examplesBaseDir.resolve("Loan/example-complex-loan.json");
        assertTrue(Files.exists(exampleFile));

        Loan readBack = objectMapper.readValue(exampleFile.toFile(), Loan.class);
        assertNotNull(readBack);
        assertEquals(loan.getLoanId(), readBack.getLoanId());
    }

    @Test
    @DisplayName("Generate Party example")
    void generatePartyExample() throws Exception {
        // Given
        Party party = new Party();
        party.setPartyId("PARTY-001");
        party.setLegalName("Acme Corporation Ltd");
        party.setJurisdiction("England and Wales");
        party.setLei("213800ABCDEF1234567890");
        party.setRole("Borrower");

        Party.PartyContact contact = new Party.PartyContact();
        contact.setContactName("John Smith");
        contact.setEmail("john.smith@acmecorp.com");
        contact.setPhone("+44 20 7123 4567");
        party.setContact(contact);

        Party.PartyAddress address = new Party.PartyAddress();
        address.setLine1("123 Business Park");
        address.setLine2("Suite 400");
        address.setCity("London");
        address.setPostcode("EC1A 1BB");
        address.setCountry("United Kingdom");
        party.setAddress(address);

        // When
        writeExampleToFile("Party", "example-borrower.json", party);

        // Then
        Path exampleFile = examplesBaseDir.resolve("Party/example-borrower.json");
        assertTrue(Files.exists(exampleFile));

        Party readBack = objectMapper.readValue(exampleFile.toFile(), Party.class);
        assertNotNull(readBack);
        assertEquals(party.getPartyId(), readBack.getPartyId());
    }

    @Test
    @DisplayName("Generate Payment example")
    void generatePaymentExample() throws Exception {
        // Given
        Payment payment = new Payment();
        payment.setPaymentId("PAY-2024-001");
        payment.setLoanId("LOAN-2024-001");
        payment.setPayerPartyId("PARTY-001");
        payment.setPaymentAmount(new BigDecimal("15000.00"));
        payment.setCurrency("GBP");
        payment.setValueDate(LocalDate.of(2024, 2, 15));
        payment.setReceivedDate(LocalDate.of(2024, 2, 15));
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setReference("WIRE-REF-123456");

        Payment.PaymentAllocation allocation = new Payment.PaymentAllocation();
        allocation.setInterestAllocated(new BigDecimal("4791.67"));
        allocation.setFeesAllocated(new BigDecimal("0.00"));
        allocation.setPrincipalAllocated(new BigDecimal("10208.33"));
        allocation.setExcessFunds(new BigDecimal("0.00"));
        payment.setAllocation(allocation);

        Payment.PaymentAudit audit = new Payment.PaymentAudit();
        audit.setCreatedAt(LocalDateTime.of(2024, 2, 15, 10, 30, 0));
        audit.setCreatedBy("system");
        audit.setPostedAt(LocalDateTime.of(2024, 2, 15, 10, 35, 0));
        audit.setPostedBy("system");
        payment.setAudit(audit);

        // When
        writeExampleToFile("Payment", "example-payment.json", payment);

        // Then
        Path exampleFile = examplesBaseDir.resolve("Payment/example-payment.json");
        assertTrue(Files.exists(exampleFile));

        Payment readBack = objectMapper.readValue(exampleFile.toFile(), Payment.class);
        assertNotNull(readBack);
        assertEquals(payment.getPaymentId(), readBack.getPaymentId());
    }

    @Test
    @DisplayName("Generate SettlementQuote example")
    void generateSettlementQuoteExample() throws Exception {
        // Given
        SettlementQuote quote = new SettlementQuote();
        quote.setQuoteId("QUOTE-2024-001");
        quote.setLoanId("LOAN-2024-001");
        quote.setSettlementDate(LocalDate.of(2024, 6, 30));
        quote.setExpirationDate(LocalDate.of(2024, 6, 15));
        quote.setRequestedBy("borrower-user-001");

        SettlementQuote.SettlementCalculation calculation = new SettlementQuote.SettlementCalculation();
        calculation.setOutstandingPrincipal(new BigDecimal("950000.00"));
        calculation.setAccruedInterestToDate(new BigDecimal("12500.00"));
        calculation.setProjectedInterestToSettlement(new BigDecimal("2300.00"));
        calculation.setFees(new BigDecimal("500.00"));
        calculation.setBreakCosts(new BigDecimal("5000.00"));
        calculation.setTotalInterest(new BigDecimal("14800.00"));
        calculation.setCalculationMethod("ACT/365");
        calculation.setDayCountBasis("ACT/365");
        quote.setCalculation(calculation);

        quote.setTotalAmountDue(new BigDecimal("970300.00"));
        quote.setCurrency("GBP");

        SettlementQuote.SettlementAudit audit = new SettlementQuote.SettlementAudit();
        audit.setCreatedAt(LocalDateTime.of(2024, 6, 1, 10, 0, 0));
        audit.setCreatedBy("system");
        audit.setQuotedAt(LocalDateTime.of(2024, 6, 1, 10, 5, 0));
        audit.setQuotedBy("finance-user-001");
        quote.setAudit(audit);

        // When
        writeExampleToFile("SettlementQuote", "example-settlement-quote.json", quote);

        // Then
        Path exampleFile = examplesBaseDir.resolve("SettlementQuote/example-settlement-quote.json");
        assertTrue(Files.exists(exampleFile));

        SettlementQuote readBack = objectMapper.readValue(exampleFile.toFile(), SettlementQuote.class);
        assertNotNull(readBack);
        assertEquals(quote.getQuoteId(), readBack.getQuoteId());
    }

    /**
     * Helper method to write an entity example to a JSON file
     */
    private void writeExampleToFile(String entityName, String fileName, Object entity) throws Exception {
        Path entityDir = examplesBaseDir.resolve(entityName);
        Files.createDirectories(entityDir);

        Path exampleFile = entityDir.resolve(fileName);
        objectMapper.writeValue(exampleFile.toFile(), entity);

        System.out.println("✓ Generated: " + exampleFile);
    }
}

