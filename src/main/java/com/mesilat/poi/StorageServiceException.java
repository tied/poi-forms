package com.mesilat.poi;

public class StorageServiceException extends Exception {
    public StorageServiceException(String msg){
        super(msg);
    }
    public StorageServiceException(String msg, Throwable cause){
        super(msg, cause);
    }
    public StorageServiceException(Throwable cause){
        super(cause);
    }
}