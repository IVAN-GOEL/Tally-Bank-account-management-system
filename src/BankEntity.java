/**
 * BankEntity.java - Abstract Base Class for all Banking Entities
 * 
 * Demonstrates:
 * 1. Abstraction: Defines the abstract blueprint that every banking domain entity must fulfill.
 * 2. Abstract Methods: getEntityId(), describe(), toJson() must be implemented by concrete subclasses.
 * 3. Contract Enforcement: Both Account and Transaction extend BankEntity.
 * 
 * Syllabus Mapping:
 * - Unit: Inheritance & Abstract Classes (abstract keyword, abstract methods, base class contract)
 */
public abstract class BankEntity {

    /**
     * Abstract method returning the unique identifier of the banking entity
     * (e.g. Account Number for accounts, or generated Transaction ID).
     */
    public abstract String getEntityId();

    /**
     * Abstract method providing a human-readable description of the entity's behavior and state.
     */
    public abstract String describe();

    /**
     * Abstract method serializing the entity's state into a standardized JSON representation.
     */
    public abstract String toJson();

    /**
     * Concrete helper method available to all derived entities for JSON string escaping.
     * Demonstrates code reuse through inheritance.
     */
    protected static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
