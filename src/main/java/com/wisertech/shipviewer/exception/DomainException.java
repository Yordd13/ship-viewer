// DomainException: base class for the things this application refuses to do, mapped to HTTP by the advice.

package com.wisertech.shipviewer.exception;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
