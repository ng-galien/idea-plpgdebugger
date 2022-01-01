package org.postgres.debugger.command;

public class PlpgCommandException extends PlpgException {
    public PlpgCommandException(String errorMessage) {
        super(errorMessage);
    }

    public PlpgCommandException(String errorMessage, Exception e) {
        super(errorMessage, e);
    }
}
