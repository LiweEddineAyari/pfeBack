package projet.app.ai.shared.exception;

/**
 * Wraps any failure raised by a backend tool call (HTTP loopback) into a typed
 * exception that the GlobalExceptionHandler can map to a structured 502 response.
 */
public class ToolExecutionException extends RuntimeException {

    private final String toolName;

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
