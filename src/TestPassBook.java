
public class TestPassBook {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("          Tally Java OOP Test Suite (23 Verification Scenarios)    ");
        System.out.println("==================================================================");

        testTransaction();
        testBaseAccount();
        testSavingsAccount();
        testCurrentAccount();
        testTransfers();
        testScoreIQ();
        testSyllabusOOPFeatures();

        System.out.println("==================================================================");
        System.out.printf("Test Summary: %d PASSED, %d FAILED\n", passed, failed);
        System.out.println("==================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (condition) {
            System.out.println(" [PASS] " + testName);
            passed++;
        } else {
            System.err.println(" [FAIL] " + testName);
            failed++;
        }
    }

    private static void testTransaction() {
        Transaction tx = new Transaction("DEPOSIT", 1500.50, 4500.50, "Salary credit");
        assertTrue("DEPOSIT".equals(tx.getType()), "Transaction type is DEPOSIT");
        assertTrue(tx.getAmount() == 1500.50, "Transaction amount matches 1500.50");
        assertTrue(tx.getBalanceAfter() == 4500.50, "Transaction balance after matches 4500.50");
        assertTrue(tx.toJson().contains("\"amount\":1500.50"), "Transaction JSON serialization contains amount");
    }

    private static void testBaseAccount() {
        Account acc = new Account("Ravi Sharma", 2000.0);
        assertTrue("Ravi Sharma".equals(acc.getHolder()), "Base Account holder name matches");
        assertTrue(acc.getBalance() == 2000.0, "Base Account starting balance is 2000.0");
        assertTrue(acc.getAccountNumber().startsWith("PB"), "Base Account number starts with PB");

        try {
            acc.deposit(500.0, "Cash");
            assertTrue(acc.getBalance() == 2500.0, "Deposit 500 increases balance to 2500.0");
        } catch (Exception e) {
            assertTrue(false, "Deposit threw unexpected error: " + e.getMessage());
        }

        try {
            acc.deposit(-100.0);
            assertTrue(false, "Negative deposit did not throw InvalidAmountException");
        } catch (InvalidAmountException e) {
            assertTrue(true, "Negative deposit properly caught InvalidAmountException");
        } catch (Exception e) {
            assertTrue(false, "Negative deposit threw wrong exception: " + e);
        }

        try {
            acc.withdraw(500.0);
            assertTrue(acc.getBalance() == 2000.0, "Base withdraw decreases balance to 2000.0");
        } catch (Exception e) {
            assertTrue(false, "Base withdraw threw unexpected error: " + e.getMessage());
        }

        try {
            acc.withdraw(3000.0);
            assertTrue(false, "Base withdraw below zero did not throw InsufficientFundsException");
        } catch (InsufficientFundsException e) {
            assertTrue(true, "Base withdraw below zero properly caught InsufficientFundsException");
        } catch (Exception e) {
            assertTrue(false, "Base withdraw below zero threw wrong exception: " + e);
        }
    }

    private static void testSavingsAccount() {
        SavingsAccount sav = new SavingsAccount("Ananya Verma", 5000.0);
        assertTrue("Savings Account".equals(sav.getAccountType()), "SavingsAccount type is 'Savings Account'");

        try {
            sav.withdraw(3500.0);
            assertTrue(sav.getBalance() == 1500.0, "SavingsAccount allows withdrawal above min balance");
        } catch (Exception e) {
            assertTrue(false, "SavingsAccount withdraw failed: " + e);
        }

        try {
            sav.withdraw(1100.0);
            assertTrue(false, "SavingsAccount did not enforce ₹500 minimum balance rule");
        } catch (MinimumBalanceException e) {
            assertTrue(true, "SavingsAccount minimum balance properly caught MinimumBalanceException");
        } catch (Exception e) {
            assertTrue(false, "SavingsAccount min balance threw wrong exception: " + e);
        }

        SavingsAccount limitAcc = new SavingsAccount("Limit Tester", 10000.0);
        try {
            for (int i = 0; i < 5; i++) {
                limitAcc.withdraw(100.0);
            }
            assertTrue(limitAcc.getBalance() == 9500.0, "SavingsAccount completed 5 valid withdrawals");
        } catch (Exception e) {
            assertTrue(false, "SavingsAccount 5 withdrawals threw error: " + e);
        }

        try {
            limitAcc.withdraw(100.0);
            assertTrue(false, "SavingsAccount 6th withdrawal did not throw WithdrawalLimitExceededException");
        } catch (WithdrawalLimitExceededException e) {
            assertTrue(true, "SavingsAccount 6th withdrawal properly caught WithdrawalLimitExceededException");
        } catch (Exception e) {
            assertTrue(false, "SavingsAccount 6th withdrawal threw wrong exception: " + e);
        }

        SavingsAccount intAcc = new SavingsAccount("Interest Tester", 12000.0);
        try {
            Transaction tx = intAcc.monthEnd();
            assertTrue(tx != null && tx.getAmount() == 35.0, "SavingsAccount monthEnd calculated ₹35.00 interest");
            assertTrue(intAcc.getBalance() == 12035.0, "SavingsAccount balance increased by interest to ₹12,035.00");
        } catch (Exception e) {
            assertTrue(false, "SavingsAccount monthEnd threw error: " + e);
        }
    }

    private static void testCurrentAccount() {
        CurrentAccount curr = new CurrentAccount("Kavita Enterprises", 2000.0);
        assertTrue("Current Account".equals(curr.getAccountType()), "CurrentAccount type is 'Current Account'");

        try {
            curr.withdraw(5000.0);
            assertTrue(curr.getBalance() == -3000.0, "CurrentAccount allows overdraft to -₹3,000.00");
            curr.withdraw(6500.0);
            assertTrue(curr.getBalance() == -9500.0, "CurrentAccount allows overdraft down to -₹9,500.00");
        } catch (Exception e) {
            assertTrue(false, "CurrentAccount overdraft withdrawal threw error: " + e);
        }

        try {
            curr.withdraw(600.0);
            assertTrue(false, "CurrentAccount breaching overdraft did not throw InsufficientFundsException");
        } catch (InsufficientFundsException e) {
            assertTrue(true, "CurrentAccount breaching overdraft properly caught InsufficientFundsException");
        } catch (Exception e) {
            assertTrue(false, "CurrentAccount overdraft breach threw wrong exception: " + e);
        }

        CurrentAccount feeAcc = new CurrentAccount("Fee Tester", 2000.0);
        try {
            Transaction tx = feeAcc.monthEnd();
            assertTrue(tx != null && tx.getAmount() == 100.0, "CurrentAccount monthEnd deducted flat ₹100 fee");
            assertTrue(feeAcc.getBalance() == 1900.0, "CurrentAccount balance updated to ₹1,900.00");
        } catch (Exception e) {
            assertTrue(false, "CurrentAccount monthEnd threw error: " + e);
        }
    }

    private static void testTransfers() {
        Account a1 = new Account("Sender", 2000.0);
        Account a2 = new Account("Receiver", 1000.0);

        try {
            Transaction[] txs = a1.transfer(a2, 500.0);
            assertTrue(a1.getBalance() == 1500.0, "Transfer debited source balance to ₹1,500.00");
            assertTrue(a2.getBalance() == 1500.0, "Transfer credited target balance to ₹1,500.00");
            assertTrue(txs.length == 2, "Transfer returned both source and target transactions");
        } catch (Exception e) {
            assertTrue(false, "Transfer threw error: " + e);
        }

        try {
            a1.transfer(a1, 100.0);
            assertTrue(false, "Transfer to self did not throw BankException");
        } catch (BankException e) {
            assertTrue(true, "Transfer to self properly threw BankException");
        }
    }

    private static void testScoreIQ() {
        SavingsAccount disciplined = new SavingsAccount("Disciplined User", 6000.0);
        try {
            disciplined.deposit(2000.0, "Payout 1");
            disciplined.deposit(3000.0, "Payout 2");
            disciplined.deposit(2500.0, "Payout 3");
            disciplined.deposit(1500.0, "Payout 4");
            disciplined.withdraw(1000.0, "Groceries");
        } catch (Exception ignored) {}

        ScoreIQ.ScoreResult res = ScoreIQ.calculate(disciplined);
        assertTrue(res.score >= 300 && res.score <= 900, "ScoreIQ score is within 300-900 bounds");
        assertTrue("Good".equals(res.band) || "Excellent".equals(res.band), "Disciplined account receives Good or Excellent band");
        assertTrue(res.breakdown.size() == 4, "ScoreIQ evaluates 4 factors");
        assertTrue(!res.tips.isEmpty(), "ScoreIQ provides improvement tips");

        CurrentAccount inOverdraft = new CurrentAccount("Overdraft User", -4000.0);
        ScoreIQ.ScoreResult odRes = ScoreIQ.calculate(inOverdraft);
        assertTrue(odRes.score <= 650, "Overdrafted account receives lower score");
    }

    private static void testSyllabusOOPFeatures() {
        System.out.println("\n--- Testing Syllabus Concepts (Inheritance, Abstraction, Recursion, I/O) ---");

        BankEntity entity = new Account("Kavita Rao", 10000.0);
        assertTrue(entity.getEntityId().startsWith("PB"), "BankEntity polymorphic getEntityId returns account number");
        assertTrue(entity.describe().contains("Base Account"), "BankEntity polymorphic describe() works");

        SeniorCitizenSavingsAccount senior = new SeniorCitizenSavingsAccount("Dada Ji", 20000.0);
        assertTrue("Senior Citizen Savings Account".equals(senior.getAccountType()), "Multilevel subclass identifies as Senior Citizen");
        assertTrue(senior instanceof SavingsAccount, "SeniorCitizenSavingsAccount is an instance of SavingsAccount");
        assertTrue(senior instanceof Account, "SeniorCitizenSavingsAccount is an instance of Account");
        assertTrue(senior instanceof BankEntity, "SeniorCitizenSavingsAccount is an instance of BankEntity");

        try {
            Transaction interestTx = senior.monthEnd();

            assertTrue(interestTx != null && interestTx.getAmount() == 70.83, "Senior citizen preferential 4.25% p.a. interest calculated (₹70.83)");
            assertTrue(senior.getBalance() == 20070.83, "Senior citizen balance updated with interest");
        } catch (Exception e) {
            assertTrue(false, "Senior citizen monthEnd threw exception: " + e.getMessage());
        }

        Account.StatementSummary summary = senior.getStatementSummary();
        assertTrue(summary.closingBalance == 20070.83, "StatementSummary static nested class holds correct balance");
        assertTrue(summary.totalDeposits == 70.83, "StatementSummary holds total deposits including interest");

        Account.PassbookAuditor auditor = senior.new PassbookAuditor();
        assertTrue(auditor.isLedgerConsistent(), "Inner class PassbookAuditor successfully inspects outer class state");

        double sum = Account.sumTransactionsRecursively(senior.getTransactions(), 0);
        assertTrue(sum == 70.83, "sumTransactionsRecursively sums transactions recursively");

        double projected = senior.calculateCompoundInterestRecursively(1000.0, 0.10, 2);

        assertTrue(projected == 1210.0, "calculateCompoundInterestRecursively projects compound growth over 2 years (₹1,210.00)");

        String tempFile = "statement_test.txt";
        try {
            senior.exportStatementToFile(tempFile);
            String content = Account.readStatementFile(tempFile);
            assertTrue(content.contains("TALLY ACCOUNT STATEMENT"), "exportStatementToFile writes header");
            assertTrue(content.contains("Dada Ji"), "exportStatementToFile contains holder name");
            new java.io.File(tempFile).delete(); // Clean up scratch test file
        } catch (Exception e) {
            assertTrue(false, "File I/O test threw exception: " + e.getMessage());
        }

        String sampleStatement = 
            "2026-03-01, Swiggy Partner Payout, 9500.00, CREDIT\n" +
            "2026-03-05, Petrol Bunk UPI, 600.00, DEBIT\n" +
            "2026-03-10, Zomato Delivery Incentive, 10200.00, CREDIT\n" +
            "2026-03-15, Mobile Recharge UPI, 299.00, DEBIT\n" +
            "2026-03-20, Kirana Store UPI, 850.00, DEBIT\n" +
            "2026-03-25, Swiggy Partner Payout, 9800.00, CREDIT";
        ScoreIQ.StatementReview review = ScoreIQ.reviewStatement(sampleStatement, "Rajesh Kumar", "gig worker");
        assertTrue(review.scoreResult.score >= 700, "External statement generates high ScoreIQ score for disciplined gig worker");
        assertTrue(review.verdict.contains("RECOMMENDED") || review.verdict.contains("APPROVAL"), "Statement review generates positive verdict");
        assertTrue(review.eligibleLimit > 0, "Statement review assigns eligible credit limit");
        assertTrue(review.totalCredits == 29500.0, "Statement review parses total credits accurately (₹29,500.00)");
        assertTrue(review.totalDebits == 1749.0, "Statement review parses total debits accurately (₹1,749.00)");
    }
}
