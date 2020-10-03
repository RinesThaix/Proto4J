package sexy.kostya.proto4j.serialization.exception;

import sexy.kostya.proto4j.commons.Proto4jException;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Proto4jSerializationException extends Proto4jException {

    public Proto4jSerializationException() {
    }

    public Proto4jSerializationException(String message) {
        super(message);
    }

    public Proto4jSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public Proto4jSerializationException(Throwable cause) {
        super(cause);
    }

    public Proto4jSerializationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
