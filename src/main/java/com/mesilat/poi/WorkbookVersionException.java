package com.mesilat.poi;

public class WorkbookVersionException extends WorkbookException {
    public WorkbookVersionException(String msg){
        super(msg);
    }
    public WorkbookVersionException(String msg, Throwable cause){
        super(msg, cause);
    }
    public WorkbookVersionException(Throwable cause){
        super(cause);
    }
}