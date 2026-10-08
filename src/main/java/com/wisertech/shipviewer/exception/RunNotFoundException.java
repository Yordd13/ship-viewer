// RunNotFoundException: thrown when no run has the given id or the run has no acquisition time.

package com.wisertech.shipviewer.exception;

public class RunNotFoundException extends DomainException {
    public RunNotFoundException(String message) {
        super(message);
    }
}
