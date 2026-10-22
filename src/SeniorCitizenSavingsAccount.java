import java.util.List;

/**
 * SeniorCitizenSavingsAccount.java
 * 
 * Demonstrates:
 * 1. Multilevel Hierarchy:
 *    BankEntity (Abstract) -> Account (Base) -> SavingsAccount (Derived) -> SeniorCitizenSavingsAccount (Multilevel)
 * 2. Method Overriding: Overrides monthEnd() and describe() with custom senior citizen benefits.
 * 3. Super Keyword: Invokes super constructors and extends behavior.
 * 4. Constants & Final: Uses specialized annual interest rate (4.25% p.a.).
 * 
 * Syllabus Mapping:
 * - Unit: Inheritance (Multilevel hierarchy, access modifiers, method overriding, super keyword).
 */
public class SeniorCitizenSavingsAccount extends SavingsAccount {
    public static final double SENIOR_ANNUAL_INTEREST_RATE = 0.0425; // 4.25% per annum (senior bonus)

    public SeniorCitizenSavingsAccount(String holder, double balance, String accountNumber, List<Transaction> transactions) {
        super(holder, balance, accountNumber, transactions);
    }

    public SeniorCitizenSavingsAccount(String holder, double balance) {
        super(holder, balance);
    }

    public SeniorCitizenSavingsAccount(String holder) {
        super(holder, 0.0);
    }

    /**
     * [METHOD OVERRIDE]
     * Overrides monthEnd() to apply preferential senior citizen interest rate of 4.25% p.a.
     */
    @Override
    public Transaction monthEnd() throws BankException {
        if (this.balance <= 0) {
            return null;
        }

        double monthlyInterest = Math.round((this.balance * (SENIOR_ANNUAL_INTEREST_RATE / 12.0)) * 100.0) / 100.0;
        if (monthlyInterest <= 0) {
            return null;
        }

        this.balance = Math.round((this.balance + monthlyInterest) * 100.0) / 100.0;
        Transaction tx = new Transaction(
            "INTEREST",
            monthlyInterest,
            this.balance,
            String.format("Senior Citizen Monthly Interest Credit (4.25%% p.a. on ₹%,.2f)", this.balance - monthlyInterest)
        );
        this.transactions.add(tx);
        return tx;
    }

    /**
     * [METHOD OVERRIDE]
     * Overrides describe() to detail the senior citizen privileges.
     */
    @Override
    public String describe() {
        return String.format(
            "Senior Citizen Savings Account (Multilevel Derived): Preferential 4.25%% p.a. interest, " +
            "₹%,.2f minimum balance, and dedicated banking support.",
            MINIMUM_BALANCE
        );
    }

    @Override
    public String getAccountType() {
        return "Senior Citizen Savings Account";
    }
}
