import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Account extends BankEntity {
    protected final String accountNumber;
    protected String holder;
    protected double balance;
    protected List<Transaction> transactions;

    public static class StatementSummary {
        public final String accountNumber;
        public final String holder;
        public final double closingBalance;
        public final double totalDeposits;
        public final double totalWithdrawals;
        public final double netCashflow;
        public final int transactionCount;

        public StatementSummary(String accNum, String holder, double bal, double dep, double with, int count) {
            this.accountNumber = accNum;
            this.holder = holder;
            this.closingBalance = bal;
            this.totalDeposits = dep;
            this.totalWithdrawals = with;
            this.netCashflow = Math.round((dep - with) * 100.0) / 100.0;
            this.transactionCount = count;
        }

        public String describe() {
            return String.format(
                "Account Summary for %s [A/C: %s]: Closing Balance: ₹%,.2f | Deposits: ₹%,.2f | Withdrawals: ₹%,.2f | Count: %d",
                holder, accountNumber, closingBalance, totalDeposits, totalWithdrawals, transactionCount
            );
        }
    }

    public class PassbookAuditor {
        public void performAudit() {
            System.out.println("  [Audit] Running internal audit for Account: " + accountNumber);
            System.out.printf("  [Audit] Account Holder: %s | Verified Balance: ₹%,.2f\n", holder, balance);
            System.out.println("  [Audit] Total ledger records: " + transactions.size());
        }

        public boolean isLedgerConsistent() {

            return balance >= -10000.0 && transactions != null;
        }
    }

    public Account(String holder, double balance, String accountNumber, List<Transaction> transactions) {
        if (holder == null || holder.trim().isEmpty()) {
            throw new IllegalArgumentException("Account holder name cannot be empty.");
        }
        this.holder = holder.trim();
        this.balance = Math.round(balance * 100.0) / 100.0;
        this.accountNumber = (accountNumber != null && !accountNumber.trim().isEmpty()) 
                             ? accountNumber 
                             : generateAccountNumber();
        this.transactions = (transactions != null) ? new ArrayList<>(transactions) : new ArrayList<>();
    }

    public Account(String holder, double balance) {
        this(holder, balance, null, null);
    }

    public Account(String holder) {
        this(holder, 0.0);
    }

    protected static String generateAccountNumber() {
        Random rand = new Random();
        long num = 10000000L + (long)(rand.nextDouble() * 89999999L);
        return "PB" + num;
    }

    public Transaction deposit(double amount, String description) throws InvalidAmountException {
        amount = Math.round(amount * 100.0) / 100.0;
        if (amount <= 0) {
            throw new InvalidAmountException("Deposit amount must be greater than ₹0.");
        }

        this.balance = Math.round((this.balance + amount) * 100.0) / 100.0;
        String desc = (description != null && !description.trim().isEmpty()) ? description : "Cash Deposit";
        Transaction tx = new Transaction("DEPOSIT", amount, this.balance, desc);
        this.transactions.add(tx);
        return tx;
    }

    public Transaction deposit(double amount) throws InvalidAmountException {
        return deposit(amount, "Cash Deposit");
    }

    public Transaction withdraw(double amount, String description) throws BankException {
        amount = Math.round(amount * 100.0) / 100.0;
        if (amount <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be greater than ₹0.");
        }

        if (this.balance - amount < 0) {
            throw new InsufficientFundsException(String.format(
                "Insufficient funds: Balance is ₹%,.2f, cannot withdraw ₹%,.2f.", this.balance, amount
            ));
        }

        this.balance = Math.round((this.balance - amount) * 100.0) / 100.0;
        String desc = (description != null && !description.trim().isEmpty()) ? description : "Cash Withdrawal";
        Transaction tx = new Transaction("WITHDRAWAL", amount, this.balance, desc);
        this.transactions.add(tx);
        return tx;
    }

    public Transaction withdraw(double amount) throws BankException {
        return withdraw(amount, "Cash Withdrawal");
    }

    
    public Transaction[] transfer(Account target, double amount) throws BankException {
        amount = Math.round(amount * 100.0) / 100.0;
        if (amount <= 0) {
            throw new InvalidAmountException("Transfer amount must be greater than ₹0.");
        }
        if (target == null) {
            throw new BankException("Beneficiary target account cannot be null.");
        }
        if (target.getAccountNumber().equals(this.accountNumber)) {
            throw new BankException("Cannot transfer funds to the same account.");
        }

        Transaction srcTx = this.withdraw(amount, String.format("Transfer to A/C %s (%s)", target.getAccountNumber(), target.getHolder()));

        Transaction destTx = target.deposit(amount, String.format("Transfer from A/C %s (%s)", this.accountNumber, this.holder));

        return new Transaction[]{srcTx, destTx};
    }

    
    public Transaction monthEnd() throws BankException {
        return null;
    }

    
    public StatementSummary getStatementSummary() {
        double totalDep = 0.0;
        double totalWith = 0.0;
        for (Transaction tx : transactions) {
            if ("DEPOSIT".equalsIgnoreCase(tx.getType()) || "TRANSFER_IN".equalsIgnoreCase(tx.getType()) || "INTEREST".equalsIgnoreCase(tx.getType())) {
                totalDep += tx.getAmount();
            } else if ("WITHDRAWAL".equalsIgnoreCase(tx.getType()) || "TRANSFER_OUT".equalsIgnoreCase(tx.getType()) || "FEE".equalsIgnoreCase(tx.getType())) {
                totalWith += tx.getAmount();
            }
        }
        return new StatementSummary(
            accountNumber, holder, balance,
            Math.round(totalDep * 100.0) / 100.0,
            Math.round(totalWith * 100.0) / 100.0,
            transactions.size()
        );
    }

    
    public static double sumTransactionsRecursively(List<Transaction> list, int index) {

        if (list == null || index >= list.size()) {
            return 0.0;
        }

        return list.get(index).getAmount() + sumTransactionsRecursively(list, index + 1);
    }

    
    public double calculateCompoundInterestRecursively(double principal, double annualRate, int yearsRemaining) {

        if (yearsRemaining <= 0) {
            return Math.round(principal * 100.0) / 100.0;
        }

        double accumulated = principal * (1.0 + annualRate);
        return calculateCompoundInterestRecursively(accumulated, annualRate, yearsRemaining - 1);
    }

    
    public void exportStatementToFile(String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("==================================================================");
            writer.println("                     TALLY ACCOUNT STATEMENT                      ");
            writer.println("==================================================================");
            writer.printf("Account Number: %s\n", accountNumber);
            writer.printf("Account Holder: %s\n", holder);
            writer.printf("Account Type  : %s\n", getAccountType());
            writer.printf("Current Balance: ₹%,.2f\n", balance);
            writer.println("------------------------------------------------------------------");
            writer.printf("%-18s | %-12s | %10s | %12s | %s\n", "Date", "Type", "Amount (₹)", "Balance (₹)", "Description");
            writer.println("------------------------------------------------------------------");
            for (Transaction tx : transactions) {
                writer.printf("%-18s | %-12s | %10.2f | %12.2f | %s\n",
                    tx.getDateStr(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(), tx.getDescription());
            }
            writer.println("==================================================================");
        }
    }

    
    public static String readStatementFile(String filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String getEntityId() {
        return accountNumber;
    }

    @Override
    public String describe() {
        return "Base Account with standard zero-floor withdrawal capabilities.";
    }

    public String getAccountType() {
        return "Standard Account";
    }

    public String getAccountNumber() { return accountNumber; }
    public String getHolder() { return holder; }
    public double getBalance() { return balance; }
    public List<Transaction> getTransactions() { return Collections.unmodifiableList(transactions); }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"account_number\":\"").append(accountNumber).append("\",");
        sb.append("\"holder\":\"").append(escapeJson(holder)).append("\",");
        sb.append("\"type\":\"").append(getAccountType()).append("\",");
        sb.append(String.format(java.util.Locale.US, "\"balance\":%.2f,", balance));
        sb.append("\"transactions\":[");
        for (int i = 0; i < transactions.size(); i++) {
            sb.append(transactions.get(i).toJson());
            if (i < transactions.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}
