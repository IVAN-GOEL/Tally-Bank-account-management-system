
public abstract class BankEntity {

    
    public abstract String getEntityId();

    
    public abstract String describe();

    
    public abstract String toJson();

    
    protected static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
