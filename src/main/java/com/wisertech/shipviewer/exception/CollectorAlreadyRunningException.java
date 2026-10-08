// CollectorAlreadyRunningException: thrown when a collector start is asked for while one already runs.

package com.wisertech.shipviewer.exception;

public class CollectorAlreadyRunningException extends DomainException {
    public CollectorAlreadyRunningException(String message) {
        super(message);
    }
}
