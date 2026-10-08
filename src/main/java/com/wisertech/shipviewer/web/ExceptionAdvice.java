// handleRunNotFound: answers an unknown run with 404 and the exception's message.
// handleAlreadyRunning: answers a second job or collector start with 409 and the exception's message.
// handleIoFailure: answers an I/O failure with 500 saying the pipeline's output could not be read.

package com.wisertech.shipviewer.web;

import com.wisertech.shipviewer.exception.CollectorAlreadyRunningException;
import com.wisertech.shipviewer.exception.DomainException;
import com.wisertech.shipviewer.exception.JobAlreadyRunningException;
import com.wisertech.shipviewer.exception.RunNotFoundException;
import com.wisertech.shipviewer.web.dto.ApiError;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionAdvice {
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RunNotFoundException.class)
    public ApiError handleRunNotFound(RunNotFoundException exception) {
        return new ApiError(exception.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler({JobAlreadyRunningException.class, CollectorAlreadyRunningException.class})
    public ApiError handleAlreadyRunning(DomainException exception) {
        return new ApiError(exception.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IOException.class)
    public ApiError handleIoFailure(IOException exception) {
        return new ApiError("Could not read the pipeline's output: " + exception.getMessage());
    }
}
