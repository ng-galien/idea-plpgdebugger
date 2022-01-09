package com.github.nggalien.pldebugger.command;

public class PlpgException extends Exception {

    public PlpgException(String errorMessage) {
        super(errorMessage);
    }

    public PlpgException(String errorMessage, Exception e) {
        super(errorMessage, e);
    }
}
