/**
 * WithdrawalLimitExceededException.java
 * Thrown when a SavingsAccount breaches the maximum 5 withdrawals per month rule.
 */
public class WithdrawalLimitExceededException extends BankException {
    public WithdrawalLimitExceededException(String message) {
        super(message);
    }
}
