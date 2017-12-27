package com.mesilat.poi;

public class WorkbookException extends Exception {
    public WorkbookException(String msg){
        super(msg);
    }
    public WorkbookException(String msg, Throwable cause){
        super(msg, cause);
    }
    public WorkbookException(Throwable cause){
        super(cause);
    }
}