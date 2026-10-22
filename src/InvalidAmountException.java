/**
 * InvalidAmountException.java
 * Thrown when an invalid (<= 0) monetary value is passed to a banking method.
 */
public class InvalidAmountException extends BankException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
