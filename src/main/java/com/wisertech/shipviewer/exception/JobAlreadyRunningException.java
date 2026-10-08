// JobAlreadyRunningException: thrown when a second pipeline job is asked for while one is still running.

package com.wisertech.shipviewer.exception;

public class JobAlreadyRunningException extends DomainException {
    public JobAlreadyRunningException(String message) {
        super(message);
    }
}
