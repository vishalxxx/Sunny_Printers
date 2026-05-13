package utils;

/**
 * Interface for controllers that wish to provide a guard against navigating away 
 * with unsaved changes.
 */
public interface DirtySupport {
    /**
     * @return true if there are unsaved changes, false otherwise.
     */
    boolean hasUnsavedChanges();

    /**
     * Called when the user clicks 'Save' or similar from a global context (optional).
     */
    default void saveChanges() {}
}
