package com.mesilat.poi.web;

import javax.ws.rs.core.Response;

public class PoiWebException extends Exception {
    private final Response.Status status;

    public Response.Status getStatus(){
        return status;
    }

    public PoiWebException(Response.Status status, String msg){
        super(msg);
        this.status = status;
    }
    public PoiWebException(Response.Status status, String msg, Throwable cause){
        super(msg, cause);
        this.status = status;
    }
    public PoiWebException(Response.Status status, Throwable cause){
        super(cause);
        this.status = status;
    }
}