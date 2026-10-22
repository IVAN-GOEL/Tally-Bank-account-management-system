import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SavingsAccount.java
 * Demonstrates:
 * 1. Inheritance: 'extends Account'
 * 2. Method Overriding: @Override on withdraw(), monthEnd(), describe()
 * 3. Domain Rules: ₹500 minimum balance, max 5 withdrawals/month, 3.5% p.a. interest.
 */
public class SavingsAccount extends Account {
    public static final double MINIMUM_BALANCE = 500.00;
    public static final double ANNUAL_INTEREST_RATE = 0.035; // 3.5% per annum
    public static final int MAX_MONTHLY_WITHDRAWALS = 5;

    public SavingsAccount(String holder, double balance, String accountNumber, List<Transaction> transactions) {
        super(holder, balance, accountNumber, transactions);
    }

    public SavingsAccount(String holder, double balance) {
        super(holder, balance);
    }

    public SavingsAccount(String holder) {
        super(holder, 0.0);
    }

    private int getCurrentMonthWithdrawalCount() {
        String currentYm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        int count = 0;
        for (Transaction tx : transactions) {
            if ("WITHDRAWAL".equalsIgnoreCase(tx.getType())) {
                String txYm = (tx.getDateStr() != null && tx.getDateStr().length() >= 7)
                              ? tx.getDateStr().substring(0, 7)
                              : currentYm;
                if (txYm.equals(currentYm)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * [METHOD OVERRIDE]
     * Overrides Account.withdraw().
     * Enforces:
     * 1. Withdrawal count <= 5 per calendar month.
     * 2. Remaining balance must be >= ₹1,000.
     */
    @Override
    public Transaction withdraw(double amount, String description) throws BankException {
        amount = Math.round(amount * 100.0) / 100.0;
        if (amount <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be greater than ₹0.");
        }

        // Rule 1: Monthly limit
        int count = getCurrentMonthWithdrawalCount();
        if (count >= MAX_MONTHLY_WITHDRAWALS) {
            throw new WithdrawalLimitExceededException(String.format(
                "Monthly withdrawal limit reached: Maximum %d withdrawals allowed per month on Savings Accounts.",
                MAX_MONTHLY_WITHDRAWALS
            ));
        }

        // Rule 2: Minimum balance
        double newBalance = Math.round((this.balance - amount) * 100.0) / 100.0;
        if (newBalance < MINIMUM_BALANCE) {
            throw new MinimumBalanceException(String.format(
                "Minimum balance rule: Savings accounts must retain at least ₹%,.2f. " +
                "Current balance: ₹%,.2f, Maximum withdrawable: ₹%,.2f.",
                MINIMUM_BALANCE, this.balance, Math.max(0.0, this.balance - MINIMUM_BALANCE)
            ));
        }

        this.balance = newBalance;
        String desc = (description != null && !description.trim().isEmpty()) ? description : "Cash Withdrawal";
        Transaction tx = new Transaction("WITHDRAWAL", amount, this.balance, desc);
        this.transactions.add(tx);
        return tx;
    }

    /**
     * [METHOD OVERRIDE]
     * Overrides Account.monthEnd().
     * Credits 1 month of interest at 3.5% p.a. on the closing balance.
     * Formula: monthlyInterest = balance * 0.035 / 12
     */
    @Override
    public Transaction monthEnd() throws BankException {
        if (this.balance <= 0) {
            return null;
        }

        double monthlyInterest = Math.round((this.balance * (ANNUAL_INTEREST_RATE / 12.0)) * 100.0) / 100.0;
        if (monthlyInterest <= 0) {
            return null;
        }

        this.balance = Math.round((this.balance + monthlyInterest) * 100.0) / 100.0;
        Transaction tx = new Transaction(
            "INTEREST",
            monthlyInterest,
            this.balance,
            String.format("Monthly Interest @ 3.5%% p.a. (₹%,.2f)", monthlyInterest)
        );
        this.transactions.add(tx);
        return tx;
    }

    /**
     * [METHOD OVERRIDE]
     * Overrides Account.describe().
     */
    @Override
    public String describe() {
        return String.format(
            "Savings Account: Designed for capital growth. Earns %.1f%% p.a. monthly interest. " +
            "Requires minimum balance of ₹%,.2f and maximum %d withdrawals/month.",
            ANNUAL_INTEREST_RATE * 100, MINIMUM_BALANCE, MAX_MONTHLY_WITHDRAWALS
        );
    }

    @Override
    public String getAccountType() {
        return "Savings Account";
    }
}
