package com.mesilat.poi;

import java.io.IOException;
import java.util.List;

public interface ReferenceDataService {
    List<String> find(long pageId, String file, String name) throws IOException;
}