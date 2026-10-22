/**
 * BankException.java - Base class for domain-specific banking errors.
 * Demonstrates: Inheritance in Exception Handling.
 */
public class BankException extends Exception {
    public BankException(String message) {
        super(message);
    }
}
