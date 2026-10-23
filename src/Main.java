import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final List<Account> accounts = new ArrayList<>();
    private static Account activeAccount = null;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("        Tally: Bank Account Management System (Java OOP)         ");
        System.out.println("            Featuring ScoreIQ Alternative Credit Scoring          ");
        System.out.println("==================================================================");

        SavingsAccount sa = new SavingsAccount("Asha Sharma", 15000.0);
        try {
            sa.deposit(5000.0, "Salary Inflow");
            sa.deposit(2500.0, "Freelance Gig Payout");
            sa.withdraw(1200.0, "Grocery Bills");
        } catch (Exception ignored) {}
        accounts.add(sa);

        CurrentAccount ca = new CurrentAccount("Apex Traders", 25000.0);
        try {
            ca.deposit(10000.0, "Vendor Settlement");
            ca.withdraw(8000.0, "Inventory Purchase");
        } catch (Exception ignored) {}
        accounts.add(ca);

        SeniorCitizenSavingsAccount sc = new SeniorCitizenSavingsAccount("Rameshwar Prasad (Senior Citizen)", 30000.0);
        accounts.add(sc);

        activeAccount = sa;

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select an option [1-13]: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1": createAccount(); break;
                case "2": listAndSwitchAccount(); break;
                case "3": deposit(); break;
                case "4": withdraw(); break;
                case "5": transfer(); break;
                case "6": runMonthEnd(); break;
                case "7": printStatement(); break;
                case "8": exportStatementFile(); break;
                case "9": runRecursiveInterestCalculator(); break;
                case "10": calculateScoreIQ(); break;
                case "11": reviewExternalStatement(); break;
                case "12": printOOPSyllabusMap(); break;
                case "13":
                    System.out.println("\nThank you for using Tally. Exiting system.");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please choose between 1 and 13.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n------------------------------------------------------------------");
        if (activeAccount != null) {
            System.out.printf("ACTIVE ACCOUNT: %s [%s] | Balance: ₹%,.2f\n",
                activeAccount.getHolder(), activeAccount.getAccountNumber(), activeAccount.getBalance());
            System.out.println("Type: " + activeAccount.getAccountType() + " | " + activeAccount.describe());
        } else {
            System.out.println("NO ACTIVE ACCOUNT SELECTED");
        }
        System.out.println("------------------------------------------------------------------");
        System.out.println(" 1. Open New Account (Savings / Current / Senior Citizen)");
        System.out.println(" 2. Switch Active Account");
        System.out.println(" 3. Deposit Funds                 [Account.deposit() - Overloading]");
        System.out.println(" 4. Withdraw Funds                [Subclass Overridden withdraw()]");
        System.out.println(" 5. Transfer to Another Account   [Objects as Parameters & Arrays]");
        System.out.println(" 6. Run Month-End Settlement      [Polymorphic monthEnd()]");
        System.out.println(" 7. Print Account Statement       [Account.getTransactions()]");
        System.out.println(" 8. Export Statement to File      [I/O Basics: File Handling]");
        System.out.println(" 9. Recursive Interest Calculator [Recursion in Methods]");
        System.out.println("10. ScoreIQ Credit Scoring        [Active Account Analysis]");
        System.out.println("11. Review External Statement     [ScoreIQ.reviewStatement()]");
        System.out.println("12. Display Full OOP Syllabus Map [Viva Reference & Architecture]");
        System.out.println("13. Exit System");
        System.out.println("------------------------------------------------------------------");
    }

    private static void createAccount() {
        System.out.println("\n--- OPEN NEW ACCOUNT ---");
        System.out.println("Select Account Type:");
        System.out.println(" 1. Savings Account (Derived from Account)");
        System.out.println(" 2. Current Account (Derived from Account, Overdraft feature)");
        System.out.println(" 3. Senior Citizen Savings Account (Multilevel: extends SavingsAccount)");
        System.out.print("Enter choice [1-3]: ");
        String typeChoice = scanner.nextLine().trim();

        System.out.print("Enter Account Holder Full Name: ");
        String holder = scanner.nextLine().trim();
        if (holder.isEmpty()) {
            System.out.println("Error: Name cannot be blank.");
            return;
        }

        System.out.print("Enter Initial Opening Balance (₹): ");
        double opening;
        try {
            opening = Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid numerical amount.");
            return;
        }

        Account newAcc;
        if ("1".equals(typeChoice)) {
            if (opening < SavingsAccount.MINIMUM_BALANCE) {
                System.out.printf("Error: Savings Account requires an opening deposit of at least ₹%,.2f.\n",
                    SavingsAccount.MINIMUM_BALANCE);
                return;
            }
            newAcc = new SavingsAccount(holder, opening);
            System.out.println("[OOP Execution] Instantiated SavingsAccount object extending Account.");
        } else if ("2".equals(typeChoice)) {
            newAcc = new CurrentAccount(holder, opening);
            System.out.println("[OOP Execution] Instantiated CurrentAccount object extending Account.");
        } else if ("3".equals(typeChoice)) {
            if (opening < SavingsAccount.MINIMUM_BALANCE) {
                System.out.printf("Error: Senior Citizen Savings Account requires at least ₹%,.2f.\n",
                    SavingsAccount.MINIMUM_BALANCE);
                return;
            }
            newAcc = new SeniorCitizenSavingsAccount(holder, opening);
            System.out.println("[OOP Execution] Instantiated SeniorCitizenSavingsAccount (Multilevel: BankEntity -> Account -> SavingsAccount -> SeniorCitizenSavingsAccount).");
        } else {
            System.out.println("Error: Invalid account type selected.");
            return;
        }

        accounts.add(newAcc);
        activeAccount = newAcc;
        System.out.printf("Success: Account created! Account No: %s | Holder: %s\n",
            newAcc.getAccountNumber(), newAcc.getHolder());
    }

    private static void listAndSwitchAccount() {
        System.out.println("\n--- REGISTERED ACCOUNTS ---");
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            System.out.printf(" [%d] %s (%s) - %s | Balance: ₹%,.2f\n",
                i + 1, a.getHolder(), a.getAccountNumber(), a.getAccountType(), a.getBalance());
        }
        System.out.print("Enter index to switch: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx >= 0 && idx < accounts.size()) {
                activeAccount = accounts.get(idx);
                System.out.printf("Switched to %s (%s).\n", activeAccount.getHolder(), activeAccount.getAccountNumber());
            } else {
                System.out.println("Error: Index out of range.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid index.");
        }
    }

    private static void deposit() {
        if (activeAccount == null) return;
        System.out.print("\nEnter Deposit Amount (₹): ");
        try {
            double amount = Double.parseDouble(scanner.nextLine().trim());
            System.out.print("Enter Narration / Remarks (or press Enter for default): ");
            String desc = scanner.nextLine().trim();

            Transaction tx;
            if (desc.isEmpty()) {

                tx = activeAccount.deposit(amount);
                System.out.println("[OOP Execution] Ran overloaded method Account.deposit(double amount).");
            } else {

                tx = activeAccount.deposit(amount, desc);
                System.out.println("[OOP Execution] Ran overloaded method Account.deposit(double amount, String description).");
            }
            System.out.printf("Success: Credited ₹%,.2f. New Balance: ₹%,.2f.\n", tx.getAmount(), activeAccount.getBalance());
        } catch (InvalidAmountException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid numerical amount.");
        }
    }

    private static void withdraw() {
        if (activeAccount == null) return;
        System.out.print("\nEnter Withdrawal Amount (₹): ");
        try {
            double amount = Double.parseDouble(scanner.nextLine().trim());
            System.out.print("Enter Narration / Remarks (or press Enter for default): ");
            String desc = scanner.nextLine().trim();

            Transaction tx;
            if (desc.isEmpty()) {
                tx = activeAccount.withdraw(amount);
            } else {
                tx = activeAccount.withdraw(amount, desc);
            }

            System.out.printf("[OOP Execution] Polymorphic call: Executed %s.withdraw() (overridden method).\n",
                activeAccount.getClass().getSimpleName());
            System.out.printf("Success: Withdrew ₹%,.2f. New Balance: ₹%,.2f.\n", tx.getAmount(), activeAccount.getBalance());
        } catch (BankException e) {
            System.out.printf("Banking Rule Exception [%s]: %s\n", e.getClass().getSimpleName(), e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid numerical amount.");
        }
    }

    private static void transfer() {
        if (activeAccount == null) return;
        if (accounts.size() < 2) {
            System.out.println("Error: At least two registered accounts are required to execute a fund transfer.");
            return;
        }

        System.out.println("\nSelect Beneficiary Target Account:");
        List<Account> others = new ArrayList<>();
        int count = 1;
        for (Account a : accounts) {
            if (!a.getAccountNumber().equals(activeAccount.getAccountNumber())) {
                others.add(a);
                System.out.printf(" [%d] %s (%s) - %s | Balance: ₹%,.2f\n",
                    count++, a.getHolder(), a.getAccountNumber(), a.getAccountType(), a.getBalance());
            }
        }

        System.out.print("Enter choice: ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (choice < 0 || choice >= others.size()) {
                System.out.println("Error: Invalid selection.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid number.");
            return;
        }

        Account target = others.get(choice);
        System.out.printf("Enter Transfer Amount to %s (₹): ", target.getHolder());
        try {
            double amount = Double.parseDouble(scanner.nextLine().trim());

            activeAccount.transfer(target, amount);

            System.out.println("[OOP Execution] Ran activeAccount.transfer(Account target, double amount).");
            System.out.println("                Demonstrates Objects as Parameters & Passing Object References.");
            System.out.printf("Success: Transferred ₹%,.2f to %s.\n", amount, target.getHolder());
            System.out.printf(" - Source Balance: ₹%,.2f (Debited)\n", activeAccount.getBalance());
            System.out.printf(" - Target Balance: ₹%,.2f (Credited)\n", target.getBalance());
        } catch (BankException e) {
            System.out.printf("Banking Rule Exception [%s]: %s\n", e.getClass().getSimpleName(), e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid numerical amount.");
        }
    }

    private static void runMonthEnd() {
        if (activeAccount == null) return;
        System.out.printf("\nExecuting Month-End Settlement for %s (%s)...\n",
            activeAccount.getHolder(), activeAccount.getAccountType());

        try {
            Transaction tx = activeAccount.monthEnd();
            System.out.printf("[OOP Execution] Polymorphic call: Executed %s.monthEnd().\n",
                activeAccount.getClass().getSimpleName());

            if (tx == null) {
                System.out.println("Notice: No adjustments required for this account type.");
            } else if ("INTEREST".equals(tx.getType())) {
                System.out.printf("Success: Credited interest of ₹%,.2f. New Balance: ₹%,.2f.\n",
                    tx.getAmount(), activeAccount.getBalance());
            } else {
                System.out.printf("Success: Charged maintenance fee of ₹%,.2f. New Balance: ₹%,.2f.\n",
                    tx.getAmount(), activeAccount.getBalance());
            }
        } catch (BankException e) {
            System.out.println("Month-End Error: " + e.getMessage());
        }
    }

    private static void printStatement() {
        if (activeAccount == null) return;
        System.out.println("\n==================================================================");
        System.out.printf("        TALLY ACCOUNT STATEMENT: %s [%s]\n", activeAccount.getHolder().toUpperCase(), activeAccount.getAccountNumber());
        System.out.printf("        Account Type: %s | Current Balance: ₹%,.2f\n", activeAccount.getAccountType(), activeAccount.getBalance());
        System.out.println("==================================================================");
        System.out.printf(" %-16s | %-12s | %10s | %11s | %s\n", "Date", "Type", "Amount (₹)", "Balance (₹)", "Narration");
        System.out.println("------------------------------------------------------------------");

        List<Transaction> txs = activeAccount.getTransactions();
        if (txs.isEmpty()) {
            System.out.println(" No transactions recorded in this ledger.");
        } else {
            for (Transaction tx : txs) {
                System.out.printf(" %-16s | %-12s | %10.2f | %11.2f | %s\n",
                    tx.getDateStr(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(), tx.getDescription());
            }
        }
        System.out.println("------------------------------------------------------------------");

        Account.StatementSummary summary = activeAccount.getStatementSummary();
        System.out.printf(" [Static Nested Class Summary] Total Inflow: ₹%,.2f | Total Outflow: ₹%,.2f | Ledger Records: %d\n",
            summary.totalDeposits, summary.totalWithdrawals, summary.transactionCount);

        Account.PassbookAuditor auditor = activeAccount.new PassbookAuditor();
        auditor.performAudit();
        System.out.println("==================================================================");
    }

    private static void exportStatementFile() {
        if (activeAccount == null) return;
        String fileName = "statement_" + activeAccount.getAccountNumber() + ".txt";
        System.out.printf("\nExporting account ledger to file: %s ...\n", fileName);

        try {

            activeAccount.exportStatementToFile(fileName);
            System.out.println("[OOP Execution] Ran activeAccount.exportStatementToFile() using FileWriter & PrintWriter.");
            System.out.println("File written successfully!");

            System.out.println("\nVerifying by reading file back via BufferedReader...");
            String content = Account.readStatementFile(fileName);
            System.out.println("File Preview (First 4 lines):");
            String[] lines = content.split("\n");
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                System.out.println(" > " + lines[i]);
            }
            System.out.printf("File path: %s (Total bytes: %d)\n", fileName, content.length());
        } catch (IOException e) {
            System.out.println("File I/O Error: " + e.getMessage());
        }
    }

    private static void runRecursiveInterestCalculator() {
        if (activeAccount == null) return;
        System.out.println("\n--- RECURSIVE COMPOUND INTEREST CALCULATOR ---");
        System.out.println("Demonstrates: Recursion with base case and recursive call");
        System.out.printf("Current Principal Balance: ₹%,.2f\n", activeAccount.getBalance());
        System.out.print("Enter number of years for projection [1-10]: ");

        try {
            int years = Integer.parseInt(scanner.nextLine().trim());
            if (years <= 0 || years > 30) {
                System.out.println("Please enter a valid duration (1-30 years).");
                return;
            }

            double rate = 0.04; // 4% p.a.
            double projected = activeAccount.calculateCompoundInterestRecursively(activeAccount.getBalance(), rate, years);
            System.out.printf("[OOP Execution] Computed compound growth recursively across %d years @ 4.0%% p.a.\n", years);
            System.out.printf("Initial Principal   : ₹%,.2f\n", activeAccount.getBalance());
            System.out.printf("Projected Maturity  : ₹%,.2f\n", projected);
            System.out.printf("Total Interest Earned: ₹%,.2f\n", projected - activeAccount.getBalance());
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid number of years.");
        }
    }

    private static void calculateScoreIQ() {
        if (activeAccount == null) return;
        System.out.println("\n==================================================================");
        System.out.println("      ScoreIQ: Alternative Credit Score (Transparent & Rule-Based) ");
        System.out.println("      (Scores account cash flow discipline without formal loan debt)");
        System.out.println("==================================================================");

        ScoreIQ.ScoreResult res = ScoreIQ.calculate(activeAccount);
        System.out.printf(" Credit Score: %d / 900  [%s | %s]\n", res.score, res.band.toUpperCase(), res.riskBand);
        System.out.println("------------------------------------------------------------------");
        System.out.println(" FACTOR BREAKDOWN (25% each):");
        for (ScoreIQ.Factor f : res.breakdown) {
            System.out.printf(" - %-22s : %3d / 100 | %s\n", f.name, f.points, f.explanation);
        }
        System.out.println("------------------------------------------------------------------");
        System.out.println(" Plain-Language Improvement Tips:");
        for (String tip : res.tips) {
            System.out.println(" * " + tip);
        }
        System.out.println("==================================================================");
    }

    private static void reviewExternalStatement() {
        System.out.println("\n==================================================================");
        System.out.println("       ScoreIQ: External Account Statement Review & Underwriting  ");
        System.out.println("==================================================================");
        System.out.println("Choose Input Method:");
        System.out.println(" 1. Load Preloaded Sample: Swiggy/Zomato Delivery Gig Partner");
        System.out.println(" 2. Load Preloaded Sample: Kirana Merchant (Daily UPI Inflows)");
        System.out.println(" 3. Load Preloaded Sample: Stressed Account (Overdraft & Bounces)");
        System.out.println(" 4. Load from local text/CSV statement file");
        System.out.print("Enter choice [1-4]: ");
        String ch = scanner.nextLine().trim();

        String stmtText;
        String applicantName;
        String empType;

        if ("1".equals(ch)) {
            applicantName = "Suresh Verma";
            empType = "gig worker";
            stmtText = 
                "2026-03-01, Swiggy Partner Weekly Payout, 9500.00, CREDIT\n" +
                "2026-03-03, Petrol Bunk HPCL UPI, 600.00, DEBIT\n" +
                "2026-03-05, Kirana Store UPI, 850.00, DEBIT\n" +
                "2026-03-08, Zomato Delivery Incentive, 10200.00, CREDIT\n" +
                "2026-03-10, Mobile Recharge Jio UPI, 299.00, DEBIT\n" +
                "2026-03-12, Bike Maintenance Mechanic, 1400.00, DEBIT\n" +
                "2026-03-15, Swiggy Partner Weekly Payout, 9800.00, CREDIT\n" +
                "2026-03-18, Grocery Mart UPI, 1600.00, DEBIT\n" +
                "2026-03-24, Zomato Weekend Surge Payout, 7500.00, CREDIT\n" +
                "2026-03-29, Room Rent UPI Transfer, 6000.00, DEBIT";
        } else if ("2".equals(ch)) {
            applicantName = "Manoj Gupta";
            empType = "self-employed";
            stmtText = 
                "2026-03-02, QR Merchant Inflow Paytm, 12500.00, CREDIT\n" +
                "2026-03-04, Wholesaler Goods Stock UPI, 8000.00, DEBIT\n" +
                "2026-03-07, QR Merchant Inflow PhonePe, 14200.00, CREDIT\n" +
                "2026-03-11, Store Electricity Bill UPI, 1850.00, DEBIT\n" +
                "2026-03-14, QR Merchant Inflow GPay, 18600.00, CREDIT\n" +
                "2026-03-18, Distributor Invoice Settlement, 12000.00, DEBIT\n" +
                "2026-03-22, Daily QR Customer Credits, 16500.00, CREDIT\n" +
                "2026-03-28, Commercial Space Rent, 7500.00, DEBIT";
        } else if ("3".equals(ch)) {
            applicantName = "Anil Sharma";
            empType = "freelance";
            stmtText = 
                "2026-03-02, Client Token Advance, 5000.00, CREDIT\n" +
                "2026-03-05, Credit Card Minimum Due UPI, 4500.00, DEBIT\n" +
                "2026-03-09, Personal Loan EMI, 6200.00, DEBIT\n" +
                "2026-03-12, ECS Dishonor Penalty Bounce, 590.00, DEBIT\n" +
                "2026-03-16, Food Delivery Order UPI, 850.00, DEBIT\n" +
                "2026-03-21, ECS Dishonor Penalty Bounce, 590.00, DEBIT";
        } else if ("4".equals(ch)) {
            System.out.print("Enter statement file path (.txt or .csv): ");
            String fPath = scanner.nextLine().trim();
            System.out.print("Enter applicant name: ");
            applicantName = scanner.nextLine().trim();
            System.out.print("Enter employment type (e.g. gig worker, self-employed): ");
            empType = scanner.nextLine().trim();
            try {
                stmtText = Account.readStatementFile(fPath);
            } catch (Exception e) {
                System.out.println("Error reading statement file: " + e.getMessage());
                return;
            }
        } else {
            System.out.println("Invalid choice.");
            return;
        }

        ScoreIQ.StatementReview rev = ScoreIQ.reviewStatement(stmtText, applicantName, empType);
        System.out.println("\n------------------------------------------------------------------");
        System.out.printf(" APPLICANT: %s (%s)\n", rev.applicantName, empType.toUpperCase());
        System.out.printf(" SCOREIQ RATING: %d / 900  [%s | %s]\n", rev.scoreResult.score, rev.scoreResult.band, rev.scoreResult.riskBand);
        System.out.println(" UNDERWRITING VERDICT: " + rev.verdict);
        if (rev.eligibleLimit > 0) {
            System.out.printf(" RECOMMENDED CREDIT LINE : ₹%,.2f\n", rev.eligibleLimit);
        }
        System.out.println("------------------------------------------------------------------");
        System.out.printf(" Total Inflows (Credits) : ₹%,.2f\n", rev.totalCredits);
        System.out.printf(" Total Outflows (Debits) : ₹%,.2f\n", rev.totalDebits);
        System.out.printf(" Net Operating Margin    : ₹%,.2f\n", rev.netMargin);
        System.out.printf(" Monthly Burn Rate       : %.1f%%\n", rev.burnRate);
        System.out.printf(" Total Transactions      : %d (%d UPI, %d Bounces)\n", rev.transactionCount, rev.upiCount, rev.bounceCount);
        System.out.println("------------------------------------------------------------------");
        System.out.println(" KEY STRENGTHS:");
        for (String s : rev.keyStrengths) System.out.println("  + " + s);
        if (!rev.riskFlags.isEmpty()) {
            System.out.println(" RISK FLAGS:");
            for (String f : rev.riskFlags) System.out.println("  - " + f);
        }
        System.out.println("------------------------------------------------------------------");
        System.out.println(" Underwriting Summary:");
        System.out.println(" " + rev.underwritingSummary);
        System.out.println("==================================================================");
    }

    private static void printOOPSyllabusMap() {
        System.out.println("\n==================================================================");
        System.out.println("            OBJECT-ORIENTED PROGRAMMING (OOP) SYLLABUS MAPPING    ");
        System.out.println("==================================================================");

        System.out.println("\n1. THE HISTORY AND EVOLUTION OF OBJECT-ORIENTED TECHNOLOGY:");
        System.out.println("   - Benefits: Modularity, Data Hiding, Code Reusability, Maintainability.");
        System.out.println("   - Application: Banking domain model with Account, Transaction, and Ledger.");
        System.out.println("   - Java vs C++ vs C#: Java uses JVM bytecode, automatic Garbage Collection,");
        System.out.println("     no raw memory pointers, and single class inheritance.");

        System.out.println("\n2. PROGRAMMING FUNDAMENTALS:");
        System.out.println("   - Control Flow: if-else conditions, switch-case menus, while and for loops.");
        System.out.println("   - Operators: Arithmetic, relational, logical (&&, ||), ternary (?:).");
        System.out.println("   - Datatypes: Primitive (double, int, boolean) and Reference (String, Account).");
        System.out.println("   - Type Conversion: Widening (int to double) and Explicit Narrowing (int) Math.round().");
        System.out.println("   - Wrapper Classes: Double.valueOf(), Integer.parseInt(), autoboxing.");
        System.out.println("   - Arrays: Fixed-size arrays Transaction[] returned by transfer().");

        System.out.println("\n3. INTRODUCTION TO CLASSES:");
        System.out.println("   - Class Fundamentals: Defining blueprints, state (fields) and behavior (methods).");
        System.out.println("   - Objects & References: Declaring objects, assigning reference variables.");
        System.out.println("   - Constructors: Constructor overloading in Account, SavingsAccount, CurrentAccount.");
        System.out.println("   - Method Overloading: deposit(amount) and deposit(amount, description).");
        System.out.println("   - Objects as Parameters: transfer(Account target, double amount).");
        System.out.println("   - Returning Objects: Returning Transaction objects and StatementSummary.");
        System.out.println("   - Recursion: sumTransactionsRecursively() and calculateCompoundInterestRecursively().");
        System.out.println("   - Access Control: private, protected (for subclasses), public (API).");
        System.out.println("   - Final Keyword: final constants (MINIMUM_BALANCE), final fields.");
        System.out.println("   - Nested & Inner Classes: Static nested StatementSummary and Inner PassbookAuditor.");

        System.out.println("\n4. I/O BASICS:");
        System.out.println("   - Console Input: Reading user input via Scanner(System.in).");
        System.out.println("   - Console Output: Formatted currency and tabular output with System.out.printf().");
        System.out.println("   - File Handling: Writing statements via FileWriter/PrintWriter; reading via BufferedReader.");

        System.out.println("\n5. INHERITANCE:");
        System.out.println("   - Base & Derived Classes: Account is base; SavingsAccount and CurrentAccount are derived.");
        System.out.println("   - Multilevel Hierarchy: BankEntity -> Account -> SavingsAccount -> SeniorCitizenSavingsAccount.");
        System.out.println("   - Access Modifiers in Inheritance: protected fields shared with child classes.");
        System.out.println("   - Method Overriding: @Override on withdraw(), monthEnd(), describe().");
        System.out.println("   - Abstract Classes: BankEntity defines abstract methods (getEntityId, describe, toJson).");

        System.out.println("\n6. EXCEPTION HANDLING:");
        System.out.println("   - Exception Types: Checked (BankException, IOException) vs Unchecked (IllegalArgumentException).");
        System.out.println("   - Custom Exceptions: BankException -> InsufficientFundsException, MinimumBalanceException,");
        System.out.println("     WithdrawalLimitExceededException, InvalidAmountException.");
        System.out.println("   - Clauses: try, catch, finally, throw, throws.");
        System.out.println("==================================================================");
    }
}
