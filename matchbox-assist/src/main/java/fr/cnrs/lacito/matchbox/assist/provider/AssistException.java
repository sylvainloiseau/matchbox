package fr.cnrs.lacito.matchbox.assist.provider;

/** A failure of the assistant, with a message that can be shown to the user. */
public class AssistException extends RuntimeException {

    private final int status;

    public AssistException(String message) {
        this(message, -1, null);
    }

    public AssistException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public AssistException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status of the failed request, -1 if not an HTTP error. */
    public int getStatus() {
        return status;
    }

    /** True for failures that may succeed if retried later (rate limit, overload, server error). */
    public boolean isTransient() {
        return status == 408 || status == 429 || status >= 500;
    }
}
