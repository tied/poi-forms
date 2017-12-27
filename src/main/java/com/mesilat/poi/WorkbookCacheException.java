package com.mesilat.poi;

public class WorkbookCacheException extends Exception {
    public WorkbookCacheException(String msg){
        super(msg);
    }
    public WorkbookCacheException(String msg, Throwable cause){
        super(msg, cause);
    }
    public WorkbookCacheException(Throwable cause){
        super(cause);
    }
}