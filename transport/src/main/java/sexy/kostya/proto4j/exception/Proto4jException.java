package sexy.kostya.proto4j.exception;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Proto4jException extends RuntimeException {

    public Proto4jException() {
    }

    public Proto4jException(String message) {
        super(message);
    }

    public Proto4jException(String message, Throwable cause) {
        super(message, cause);
    }

    public Proto4jException(Throwable cause) {
        super(cause);
    }

    public Proto4jException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
