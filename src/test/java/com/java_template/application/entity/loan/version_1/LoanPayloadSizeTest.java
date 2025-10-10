package com.java_template.application.entity.loan.version_1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to measure the JSON payload size of a maximally populated Loan entity.
 * This represents the upper limit of what a fully specified commercial loan might look like.
 */
class LoanPayloadSizeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // For readable output
    }

    @Test
    @DisplayName("Measure JSON payload size of maximally populated Loan entity")
    void testMaximalLoanPayloadSize() throws Exception {
        // Given - Create a maximally populated loan
        Loan maximalLoan = createMaximalLoan();

        // When - Serialize to JSON
        String jsonString = objectMapper.writeValueAsString(maximalLoan);
        byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        int sizeInBytes = jsonBytes.length;
        double sizeInKB = sizeInBytes / 1024.0;
        double sizeInMB = sizeInKB / 1024.0;

        // Then - Print size information
        System.out.println("=".repeat(80));
        System.out.println("MAXIMAL LOAN ENTITY JSON PAYLOAD SIZE ANALYSIS");
        System.out.println("=".repeat(80));
        System.out.println("Size in bytes: " + String.format("%,d", sizeInBytes));
        System.out.println("Size in KB:    " + String.format("%.2f", sizeInKB));
        System.out.println("Size in MB:    " + String.format("%.4f", sizeInMB));
        System.out.println("=".repeat(80));
        System.out.println("\nJSON Structure Summary:");
        System.out.println("- Parties: " + maximalLoan.getParties().size());
        System.out.println("- Facilities: " + maximalLoan.getFacilities().size());
        
        if (!maximalLoan.getFacilities().isEmpty()) {
            Loan.LoanFacility firstFacility = maximalLoan.getFacilities().get(0);
            System.out.println("  - Tranches per facility: " + firstFacility.getTranches().size());
            System.out.println("  - Drawdowns per facility: " + firstFacility.getDrawdowns().size());
            System.out.println("  - Repayments per facility: " + firstFacility.getRepayments().size());
            
            if (!firstFacility.getTranches().isEmpty()) {
                Loan.LoanTranche firstTranche = firstFacility.getTranches().get(0);
                System.out.println("    - Fees per tranche: " + firstTranche.getFees().size());
                System.out.println("    - Covenants per tranche: " + firstTranche.getCovenants().size());
                System.out.println("    - Collateral per tranche: " + firstTranche.getCollateral().size());
            }
        }
        System.out.println("=".repeat(80));

        // Optionally print the JSON (commented out to avoid cluttering test output)
        // System.out.println("\nFull JSON:\n" + jsonString);

        // Assertions
        assertTrue(sizeInBytes > 0, "JSON size should be greater than 0");
        assertTrue(sizeInKB > 1, "JSON should be at least 1 KB for a maximal loan");
        
        // Verify the loan is valid
        assertNotNull(maximalLoan.getLoanId());
        assertNotNull(maximalLoan.getAgreementId());
        assertNotNull(maximalLoan.getPartyId());
    }

    /**
     * Creates a maximally populated Loan entity representing the upper limit
     * of complexity for a commercial loan in the system.
     */
    private Loan createMaximalLoan() {
        Loan loan = new Loan();
        
        // Required fields
        loan.setLoanId("LOAN-MAX-2024-001");
        loan.setAgreementId("AGR-SYNDICATED-2024-001");
        loan.setPartyId("PARTY-BORROWER-001");
        loan.setPrincipalAmount(new BigDecimal("500000000.00")); // $500M
        loan.setApr(new BigDecimal("5.75"));
        loan.setTermMonths(36); // 3 years
        loan.setFundingDate(LocalDate.of(2024, 1, 15));
        loan.setMaturityDate(LocalDate.of(2027, 1, 15));
        
        // Financial balances
        loan.setOutstandingPrincipal(new BigDecimal("450000000.00"));
        loan.setAccruedInterest(new BigDecimal("1250000.00"));
        
        // Optional fields
        loan.setPurpose("Acquisition financing and general corporate purposes");
        loan.setGoverningLaw("England and Wales");
        loan.setDayCountBasis("ACT/365");
        loan.setCurrency("GBP");
        
        // Validation error tracking
        loan.setValidationErrorReason(null);
        
        // Create multiple parties (borrower, lenders, agent, security trustee)
        loan.setParties(createMaximalParties());
        
        // Create multiple facilities (revolver + term loan)
        loan.setFacilities(createMaximalFacilities());
        
        return loan;
    }

    private List<Loan.LoanParty> createMaximalParties() {
        List<Loan.LoanParty> parties = new ArrayList<>();
        
        // Borrower
        Loan.LoanParty borrower = new Loan.LoanParty();
        borrower.setPartyId("PARTY-BORROWER-001");
        borrower.setName("Acme Corporation PLC");
        borrower.setLei("549300ABCDEF1234567890");
        borrower.setRole("Borrower");
        borrower.setJurisdiction("England and Wales");
        borrower.setCommitmentAmount(null);
        borrower.setCommitmentCurrency(null);
        parties.add(borrower);
        
        // Multiple lenders
        for (int i = 1; i <= 5; i++) {
            Loan.LoanParty lender = new Loan.LoanParty();
            lender.setPartyId("PARTY-LENDER-00" + i);
            lender.setName("Global Bank " + i + " Limited");
            lender.setLei("549300LENDER" + String.format("%010d", i));
            lender.setRole("Lender");
            lender.setJurisdiction("England and Wales");
            lender.setCommitmentAmount(new BigDecimal("100000000.00")); // $100M each
            lender.setCommitmentCurrency("GBP");
            parties.add(lender);
        }
        
        // Agent
        Loan.LoanParty agent = new Loan.LoanParty();
        agent.setPartyId("PARTY-AGENT-001");
        agent.setName("Administrative Agent Bank Limited");
        agent.setLei("549300AGENT0123456789");
        agent.setRole("Agent");
        agent.setJurisdiction("England and Wales");
        agent.setCommitmentAmount(null);
        agent.setCommitmentCurrency(null);
        parties.add(agent);
        
        // Security Trustee
        Loan.LoanParty trustee = new Loan.LoanParty();
        trustee.setPartyId("PARTY-TRUSTEE-001");
        trustee.setName("Security Trustee Services Limited");
        trustee.setLei("549300TRUSTEE123456789");
        trustee.setRole("Security Trustee");
        trustee.setJurisdiction("England and Wales");
        trustee.setCommitmentAmount(null);
        trustee.setCommitmentCurrency(null);
        parties.add(trustee);
        
        return parties;
    }

    private List<Loan.LoanFacility> createMaximalFacilities() {
        List<Loan.LoanFacility> facilities = new ArrayList<>();
        
        // Facility 1: Revolving Credit Facility
        facilities.add(createRevolverFacility());
        
        // Facility 2: Term Loan Facility
        facilities.add(createTermLoanFacility());
        
        return facilities;
    }

    private Loan.LoanFacility createRevolverFacility() {
        Loan.LoanFacility facility = new Loan.LoanFacility();
        facility.setFacilityId("FAC-REVOLVER-001");
        facility.setType("Revolver");
        facility.setCurrency("GBP");
        facility.setLimit(new BigDecimal("200000000.00")); // $200M
        
        // Availability
        facility.setAvailability(createAvailability());
        
        // Tranches
        facility.setTranches(createTranches(2)); // 2 tranches
        
        // Drawdowns
        facility.setDrawdowns(createDrawdowns(3)); // 3 drawdowns
        
        // Repayments
        facility.setRepayments(createRepayments(2)); // 2 repayments
        
        // Prepayment terms
        facility.setPrepayment(createPrepaymentTerms());
        
        return facility;
    }

    private Loan.LoanFacility createTermLoanFacility() {
        Loan.LoanFacility facility = new Loan.LoanFacility();
        facility.setFacilityId("FAC-TERMLOAN-001");
        facility.setType("Term Loan");
        facility.setCurrency("GBP");
        facility.setLimit(new BigDecimal("300000000.00")); // $300M
        
        // Availability
        facility.setAvailability(createAvailability());
        
        // Tranches
        facility.setTranches(createTranches(3)); // 3 tranches
        
        // Drawdowns
        facility.setDrawdowns(createDrawdowns(5)); // 5 drawdowns
        
        // Repayments
        facility.setRepayments(createRepayments(4)); // 4 repayments
        
        // Prepayment terms
        facility.setPrepayment(createPrepaymentTerms());
        
        return facility;
    }

    private Loan.LoanAvailability createAvailability() {
        Loan.LoanAvailability availability = new Loan.LoanAvailability();
        availability.setStartDate(LocalDate.of(2024, 1, 15));
        availability.setEndDate(LocalDate.of(2026, 1, 15));
        availability.setConditionsPrecedent(Arrays.asList(
            "Executed facility agreement",
            "Legal opinions received",
            "Security documents executed",
            "KYC documentation complete",
            "No material adverse change"
        ));
        return availability;
    }

    private List<Loan.LoanTranche> createTranches(int count) {
        List<Loan.LoanTranche> tranches = new ArrayList<>();
        
        for (int i = 1; i <= count; i++) {
            Loan.LoanTranche tranche = new Loan.LoanTranche();
            tranche.setTrancheId("TRANCHE-" + String.format("%03d", i));
            tranche.setLimit(new BigDecimal("100000000.00"));
            tranche.setPurpose("General corporate purposes - Tranche " + i);
            
            // Interest configuration
            tranche.setInterest(createInterestConfig());
            
            // Fees (3 different fee types)
            tranche.setFees(createFees());
            
            // Amortization
            tranche.setAmortization(createAmortization());
            
            // Covenants (5 covenants)
            tranche.setCovenants(createCovenants());
            
            // Collateral (3 collateral items)
            tranche.setCollateral(createCollateral());
            
            tranches.add(tranche);
        }
        
        return tranches;
    }

    private Loan.LoanInterest createInterestConfig() {
        Loan.LoanInterest interest = new Loan.LoanInterest();
        interest.setIndex("SONIA");
        interest.setTenor("3M");
        interest.setSpreadBps(275); // 2.75% spread
        interest.setFloorRate(new BigDecimal("0.00"));
        interest.setDayCount("ACT/365F");
        interest.setCompounding("Compound");

        // Rate reset
        Loan.LoanRateReset rateReset = new Loan.LoanRateReset();
        rateReset.setFrequency("Quarterly");
        rateReset.setBusinessDayConvention("ModifiedFollowing");
        interest.setRateReset(rateReset);

        return interest;
    }

    private List<Loan.LoanFee> createFees() {
        List<Loan.LoanFee> fees = new ArrayList<>();

        // Commitment fee
        Loan.LoanFee commitmentFee = new Loan.LoanFee();
        commitmentFee.setFeeId("FEE-COMMITMENT-001");
        commitmentFee.setType("Commitment");
        commitmentFee.setBasis("Unused");
        commitmentFee.setRateBps(50); // 0.50%
        commitmentFee.setAmount(null);
        commitmentFee.setAccrualDayCount("ACT/365");
        commitmentFee.setPayFrequency("Quarterly");
        commitmentFee.setPayOn("Quarterly");
        fees.add(commitmentFee);

        // Arrangement fee
        Loan.LoanFee arrangementFee = new Loan.LoanFee();
        arrangementFee.setFeeId("FEE-ARRANGEMENT-001");
        arrangementFee.setType("Arrangement");
        arrangementFee.setBasis("Outstanding");
        arrangementFee.setRateBps(null);
        arrangementFee.setAmount(new BigDecimal("2500000.00")); // $2.5M upfront
        arrangementFee.setAccrualDayCount(null);
        arrangementFee.setPayFrequency(null);
        arrangementFee.setPayOn("Signing");
        fees.add(arrangementFee);

        // Agency fee
        Loan.LoanFee agencyFee = new Loan.LoanFee();
        agencyFee.setFeeId("FEE-AGENCY-001");
        agencyFee.setType("Agency");
        agencyFee.setBasis("Outstanding");
        agencyFee.setRateBps(null);
        agencyFee.setAmount(new BigDecimal("150000.00")); // $150K annual
        agencyFee.setAccrualDayCount(null);
        agencyFee.setPayFrequency("Annual");
        agencyFee.setPayOn("Anniversary");
        fees.add(agencyFee);

        return fees;
    }

    private Loan.LoanAmortization createAmortization() {
        Loan.LoanAmortization amortization = new Loan.LoanAmortization();
        amortization.setType("Amortizing");
        amortization.setSchedule(Arrays.asList(
            "2024-07-15: 10%",
            "2025-01-15: 15%",
            "2025-07-15: 20%",
            "2026-01-15: 25%",
            "2026-07-15: 30%"
        ));
        return amortization;
    }

    private List<Loan.LoanCovenant> createCovenants() {
        List<Loan.LoanCovenant> covenants = new ArrayList<>();

        // Net Leverage covenant
        Loan.LoanCovenant netLeverage = new Loan.LoanCovenant();
        netLeverage.setCovenantId("COV-NETLEV-001");
        netLeverage.setCategory("Financial");
        netLeverage.setName("Net Leverage");
        netLeverage.setDefinition("Net Debt / EBITDA");
        netLeverage.setThresholdOperator("<=");
        netLeverage.setThresholdValue(new BigDecimal("3.50"));
        netLeverage.setTestFrequency("Quarterly");

        Loan.LoanCureRights netLevCure = new Loan.LoanCureRights();
        netLevCure.setAllowed(true);
        netLevCure.setPeriodDays(30);
        netLeverage.setCureRights(netLevCure);
        covenants.add(netLeverage);

        // Interest Coverage covenant
        Loan.LoanCovenant interestCoverage = new Loan.LoanCovenant();
        interestCoverage.setCovenantId("COV-INTCOV-001");
        interestCoverage.setCategory("Financial");
        interestCoverage.setName("Interest Coverage");
        interestCoverage.setDefinition("EBITDA / Interest Expense");
        interestCoverage.setThresholdOperator(">=");
        interestCoverage.setThresholdValue(new BigDecimal("4.00"));
        interestCoverage.setTestFrequency("Quarterly");

        Loan.LoanCureRights intCovCure = new Loan.LoanCureRights();
        intCovCure.setAllowed(true);
        intCovCure.setPeriodDays(30);
        interestCoverage.setCureRights(intCovCure);
        covenants.add(interestCoverage);

        // Minimum Liquidity covenant
        Loan.LoanCovenant minLiquidity = new Loan.LoanCovenant();
        minLiquidity.setCovenantId("COV-MINLIQ-001");
        minLiquidity.setCategory("Financial");
        minLiquidity.setName("Minimum Liquidity");
        minLiquidity.setDefinition("Cash + Undrawn Committed Facilities");
        minLiquidity.setThresholdOperator(">=");
        minLiquidity.setThresholdValue(new BigDecimal("50000000.00"));
        minLiquidity.setTestFrequency("Monthly");

        Loan.LoanCureRights liqCure = new Loan.LoanCureRights();
        liqCure.setAllowed(false);
        liqCure.setPeriodDays(null);
        minLiquidity.setCureRights(liqCure);
        covenants.add(minLiquidity);

        // Financial Statements covenant
        Loan.LoanCovenant finStatements = new Loan.LoanCovenant();
        finStatements.setCovenantId("COV-FINSTMT-001");
        finStatements.setCategory("Information");
        finStatements.setName("Financial Statements");
        finStatements.setDefinition("Delivery of audited annual and unaudited quarterly financial statements");
        finStatements.setThresholdOperator(null);
        finStatements.setThresholdValue(null);
        finStatements.setTestFrequency("Quarterly");

        Loan.LoanCureRights finCure = new Loan.LoanCureRights();
        finCure.setAllowed(false);
        finCure.setPeriodDays(null);
        finStatements.setCureRights(finCure);
        covenants.add(finStatements);

        // Compliance Certificate covenant
        Loan.LoanCovenant compCert = new Loan.LoanCovenant();
        compCert.setCovenantId("COV-COMPCERT-001");
        compCert.setCategory("Information");
        compCert.setName("Compliance Certificate");
        compCert.setDefinition("Officer's certificate confirming compliance with all covenants");
        compCert.setThresholdOperator(null);
        compCert.setThresholdValue(null);
        compCert.setTestFrequency("Quarterly");

        Loan.LoanCureRights certCure = new Loan.LoanCureRights();
        certCure.setAllowed(false);
        certCure.setPeriodDays(null);
        compCert.setCureRights(certCure);
        covenants.add(compCert);

        return covenants;
    }

    private List<Loan.LoanCollateral> createCollateral() {
        List<Loan.LoanCollateral> collateral = new ArrayList<>();

        // Debenture
        Loan.LoanCollateral debenture = new Loan.LoanCollateral();
        debenture.setCollateralId("COLL-DEB-001");
        debenture.setType("Debenture");
        debenture.setJurisdiction("England and Wales");
        debenture.setDescription("Fixed and floating charge over all assets and undertaking of the Borrower");
        collateral.add(debenture);

        // Share Pledge
        Loan.LoanCollateral sharePledge = new Loan.LoanCollateral();
        sharePledge.setCollateralId("COLL-PLEDGE-001");
        sharePledge.setType("Pledge");
        sharePledge.setJurisdiction("England and Wales");
        sharePledge.setDescription("Pledge over 100% of shares in material subsidiaries");
        collateral.add(sharePledge);

        // Account Charge
        Loan.LoanCollateral accountCharge = new Loan.LoanCollateral();
        accountCharge.setCollateralId("COLL-ACCT-001");
        accountCharge.setType("Account Charge");
        accountCharge.setJurisdiction("England and Wales");
        accountCharge.setDescription("Charge over designated bank accounts");
        collateral.add(accountCharge);

        return collateral;
    }

    private List<Loan.LoanDrawdown> createDrawdowns(int count) {
        List<Loan.LoanDrawdown> drawdowns = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            Loan.LoanDrawdown drawdown = new Loan.LoanDrawdown();
            drawdown.setDrawId("DRAW-" + String.format("%03d", i));
            drawdown.setTrancheId("TRANCHE-001");
            drawdown.setRequestDate(LocalDate.of(2024, 1, 10 + i));
            drawdown.setValueDate(LocalDate.of(2024, 1, 15 + i));
            drawdown.setAmount(new BigDecimal("50000000.00"));
            drawdown.setPurpose("Working capital drawdown " + i);

            // FX information (optional)
            if (i % 2 == 0) {
                Loan.LoanFx fx = new Loan.LoanFx();
                fx.setTradeCcy("USD");
                fx.setSettleCcy("GBP");
                fx.setRate(new BigDecimal("1.2750"));
                drawdown.setFx(fx);
            }

            drawdowns.add(drawdown);
        }

        return drawdowns;
    }

    private List<Loan.LoanRepayment> createRepayments(int count) {
        List<Loan.LoanRepayment> repayments = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            Loan.LoanRepayment repayment = new Loan.LoanRepayment();
            repayment.setRepaymentId("REPAY-" + String.format("%03d", i));
            repayment.setType(i == 1 ? "Prepayment" : "Scheduled");
            repayment.setDueDate(LocalDate.of(2024, 3 * i, 15));
            repayment.setAmount(new BigDecimal("25000000.00"));

            // Allocation
            Loan.LoanAllocation allocation = new Loan.LoanAllocation();
            allocation.setPrincipal(new BigDecimal("20000000.00"));
            allocation.setInterest(new BigDecimal("4000000.00"));
            allocation.setFees(new BigDecimal("1000000.00"));
            repayment.setAllocation(allocation);

            repayments.add(repayment);
        }

        return repayments;
    }

    private Loan.LoanPrepayment createPrepaymentTerms() {
        Loan.LoanPrepayment prepayment = new Loan.LoanPrepayment();

        // Voluntary prepayment
        Loan.LoanVoluntary voluntary = new Loan.LoanVoluntary();
        voluntary.setNoticeDays(10);
        voluntary.setBreakCostsApplicable(true);
        voluntary.setMinimumAmount(new BigDecimal("5000000.00"));
        voluntary.setMultipleAmount(new BigDecimal("1000000.00"));
        prepayment.setVoluntary(voluntary);

        // Mandatory prepayment
        Loan.LoanMandatory mandatory = new Loan.LoanMandatory();
        mandatory.setEvents(Arrays.asList(
            "Asset Sale",
            "Insurance Proceeds",
            "Debt Issuance",
            "Equity Issuance",
            "Excess Cash Flow"
        ));
        mandatory.setThreshold(new BigDecimal("10000000.00"));
        mandatory.setApplication("Pro Rata");
        prepayment.setMandatory(mandatory);

        return prepayment;
    }
}
