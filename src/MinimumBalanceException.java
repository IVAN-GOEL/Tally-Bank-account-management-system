/**
 * MinimumBalanceException.java
 * Demonstrates: Domain rule enforcement via custom exceptions.
 * Thrown when a SavingsAccount withdrawal breaches the ₹1,000 threshold.
 */
public class MinimumBalanceException extends BankException {
    public MinimumBalanceException(String message) {
        super(message);
    }
}
