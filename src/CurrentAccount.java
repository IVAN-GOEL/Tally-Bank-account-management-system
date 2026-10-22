import java.util.List;

/**
 * CurrentAccount.java
 * Demonstrates:
 * 1. Inheritance: 'extends Account'
 * 2. Method Overriding: @Override on withdraw(), monthEnd(), describe()
 * 3. Commercial Banking: Allows overdraft down to -₹10,000; charges flat ₹100 fee.
 */
public class CurrentAccount extends Account {
    public static final double OVERDRAFT_LIMIT = 10000.00;
    public static final double MONTHLY_MAINTENANCE_FEE = 100.00;

    public CurrentAccount(String holder, double balance, String accountNumber, List<Transaction> transactions) {
        super(holder, balance, accountNumber, transactions);
    }

    public CurrentAccount(String holder, double balance) {
        super(holder, balance);
    }

    public CurrentAccount(String holder) {
        super(holder, 0.0);
    }

    /**
     * [METHOD OVERRIDE]
     * Overrides Account.withdraw().
     * Permits the balance to drop below zero up to the overdraft limit (-₹10,000).
     */
    @Override
    public Transaction withdraw(double amount, String description) throws BankException {
        amount = Math.round(amount * 100.0) / 100.0;
        if (amount <= 0) {
            throw new InvalidAmountException("Withdrawal amount must be greater than ₹0.");
        }

        double minAllowedBalance = -OVERDRAFT_LIMIT;
        double newBalance = Math.round((this.balance - amount) * 100.0) / 100.0;

        if (newBalance < minAllowedBalance) {
            double availableCredit = Math.round((this.balance + OVERDRAFT_LIMIT) * 100.0) / 100.0;
            throw new InsufficientFundsException(String.format(
                "Overdraft limit breached: Maximum overdraft is ₹%,.2f. " +
                "Current balance: ₹%,.2f, Total available credit: ₹%,.2f.",
                OVERDRAFT_LIMIT, this.balance, availableCredit
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
     * Deducts a flat ₹100 monthly ledger and liquidity maintenance fee.
     */
    @Override
    public Transaction monthEnd() throws BankException {
        this.balance = Math.round((this.balance - MONTHLY_MAINTENANCE_FEE) * 100.0) / 100.0;
        Transaction tx = new Transaction(
            "FEE",
            MONTHLY_MAINTENANCE_FEE,
            this.balance,
            "Monthly Passbook & Ledger Maintenance Fee (₹100)"
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
            "Current Account: Designed for business velocity. Offers ₹%,.2f overdraft protection " +
            "with unlimited transactions. Standard ₹%,.2f/month ledger maintenance fee.",
            OVERDRAFT_LIMIT, MONTHLY_MAINTENANCE_FEE
        );
    }

    @Override
    public String getAccountType() {
        return "Current Account";
    }
}
