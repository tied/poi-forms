package com.mesilat.poi;

import java.io.InputStream;

public interface StorageService {
    boolean exists(long pageId, String file) throws StorageServiceException;
    InputStream getData(long pageId, String file) throws StorageServiceException;
    long getSize(long pageId, String file) throws StorageServiceException;
    void setData(long pageId, String file, byte[] data) throws StorageServiceException;
}