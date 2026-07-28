package pl.codetitans.odyssesus;

/**
 * Custom exception thrown by the OdysseusClient.
 */
public class OdysseusException extends RuntimeException {

    /**
     * Init constructor.
     */
    public OdysseusException(String message) {
        super(message);
    }

    /**
     * Init constructor.
     */
    public OdysseusException(String message, Throwable cause) {
        super(message, cause);
    }
}
