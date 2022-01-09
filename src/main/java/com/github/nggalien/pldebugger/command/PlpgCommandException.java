package com.github.nggalien.pldebugger.command;

public class PlpgCommandException extends PlpgException {
    public PlpgCommandException(String errorMessage) {
        super(errorMessage);
    }

    public PlpgCommandException(String errorMessage, Exception e) {
        super(errorMessage, e);
    }
}
