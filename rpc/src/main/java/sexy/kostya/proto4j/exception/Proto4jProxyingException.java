package sexy.kostya.proto4j.exception;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Proto4jProxyingException extends Proto4jException {

    public Proto4jProxyingException() {
    }

    public Proto4jProxyingException(String message) {
        super(message);
    }

    public Proto4jProxyingException(String message, Throwable cause) {
        super(message, cause);
    }

    public Proto4jProxyingException(Throwable cause) {
        super(cause);
    }

    public Proto4jProxyingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
