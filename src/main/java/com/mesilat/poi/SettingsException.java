package com.mesilat.poi;

public class SettingsException extends Exception {
    public SettingsException(String msg){
        super(msg);
    }
    public SettingsException(String msg, Throwable cause){
        super(msg, cause);
    }
    public SettingsException(Throwable cause){
        super(cause);
    }
}