import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Transaction extends BankEntity {
    private final String id;            // Unique transaction identifier
    private final String type;          // "DEPOSIT", "WITHDRAWAL", "INTEREST", "FEE", "TRANSFER_IN", "TRANSFER_OUT"
    private final double amount;        // Value in INR (always positive)
    private final double balanceAfter;  // Running balance after this transaction
    private final String dateStr;       // "YYYY-MM-DD HH:mm"
    private final String description;   // Narration / remarks

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static long counter = 1000L;

    public Transaction(String type, double amount, double balanceAfter, String dateStr, String description) {
        this.id = "TX" + (++counter);
        this.type = (type != null) ? type.toUpperCase() : "TRANSACTION";
        this.amount = Math.round(amount * 100.0) / 100.0;
        this.balanceAfter = Math.round(balanceAfter * 100.0) / 100.0;
        this.dateStr = (dateStr != null && !dateStr.trim().isEmpty()) 
                       ? dateStr 
                       : LocalDateTime.now().format(FORMATTER);
        this.description = (description != null && !description.trim().isEmpty()) 
                           ? description 
                           : (this.type.charAt(0) + this.type.substring(1).toLowerCase() + " of ₹" + String.format("%.2f", this.amount));
    }

    public Transaction(String type, double amount, double balanceAfter, String description) {
        this(type, amount, balanceAfter, null, description);
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getDateStr() { return dateStr; }
    public String getDescription() { return description; }

    @Override
    public String getEntityId() {
        return id;
    }

    @Override
    public String describe() {
        return String.format("[%s] %s: ₹%,.2f | Balance: ₹%,.2f (%s)",
            dateStr, type, amount, balanceAfter, description);
    }

    @Override
    public String toJson() {
        return String.format(
            "{\"id\":\"%s\",\"type\":\"%s\",\"amount\":%.2f,\"balance_after\":%.2f,\"date\":\"%s\",\"description\":\"%s\"}",
            escapeJson(id), escapeJson(type), amount, balanceAfter, escapeJson(dateStr), escapeJson(description)
        );
    }

    @Override
    public String toString() {
        return String.format("[%s] %-12s | ₹%10.2f | Balance: ₹%10.2f | %s",
            dateStr, type, amount, balanceAfter, description);
    }
}
