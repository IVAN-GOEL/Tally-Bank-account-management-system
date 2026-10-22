/**
 * InsufficientFundsException.java
 * Demonstrates: Subclassing a custom exception.
 * Thrown when an account lacks sufficient balance or overdraft headroom.
 */
public class InsufficientFundsException extends BankException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
