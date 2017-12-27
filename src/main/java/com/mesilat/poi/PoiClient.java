package com.mesilat.poi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class PoiClient {

    private final URL baseUrl;
    private final String authentication;
    
    public Map getToken(String fileId) throws IOException {
        CloseableHttpClient httpClient = null;
        HttpGet request = null;
        CloseableHttpResponse response = null;
        
        try {
            httpClient = HttpClients.createDefault();
            URL tokenEndpoint = new URL(baseUrl, "get-token?file-id=" + fileId);
            request = new HttpGet(tokenEndpoint.toString());
            request.setHeader("Authorization", "Basic " + authentication);
            response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Failed to get a token from a remote server");
            } else {
                HttpEntity entity = response.getEntity();
                ObjectMapper mapper = new ObjectMapper();
                InputStream contentStream = entity.getContent();
                try {
                    return mapper.readValue(entity.getContent(), Map.class);
                } finally {
                    close(contentStream);
                }
            }
        } finally {
            close(response);
            if (request != null) {
                request.reset();
            }
            close(httpClient);
        }
    }
    public Map upload(InputStream in, String mime, String fileName) throws IOException {
        CloseableHttpClient httpClient = null;
        HttpPost request = null;
        CloseableHttpResponse response = null;
        
        try {
            httpClient = HttpClients.createDefault();
            URL uploadEndpoint = new URL(baseUrl, "upload");
            request = new HttpPost(uploadEndpoint.toString());
            request.setHeader("Authorization", "Basic " + authentication);
            FormBodyPart bodyPart = FormBodyPartBuilder.create()
                .setName("file")
                .setBody(new InputStreamBody(
                    in,
                    ContentType.create(mime),
                    fileName))
                .build();
            HttpEntity content = MultipartEntityBuilder.create()
                .setCharset(Charset.forName("UTF8"))
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addPart(bodyPart)
                .build();
            request.setEntity(content);
            response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Failed to upload to a remote server");
            } else {
                HttpEntity entity = response.getEntity();
                ObjectMapper mapper = new ObjectMapper();
                InputStream contentStream = entity.getContent();
                try {
                    return mapper.readValue(entity.getContent(), Map.class);
                } finally {
                    close(contentStream);
                }
            }
        } finally {
            close(response);
            if (request != null) {
                request.reset();
            }
            close(httpClient);
        }
    }
    public Map download(String fileId, OutputStream out) throws IOException {
        CloseableHttpClient httpClient = null;
        HttpGet request = null;
        CloseableHttpResponse response = null;
        
        try {
            httpClient = HttpClients.createDefault();
            URL downloadEndpoint = new URL(baseUrl, "download?file-id=" + fileId);
            request = new HttpGet(downloadEndpoint.toString());
            request.setHeader("Authorization", "Basic " + authentication);
            response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Failed to download from remote server");
            } else {
                Header contentDisposition = response.getFirstHeader("Content-disposition");
                String filename = null;
                for (HeaderElement elt : contentDisposition.getElements()) {
                    if ("attachment".equals(elt.getName())) {
                        NameValuePair filenamePair = elt.getParameterByName("filename*");
                        if (filenamePair != null) {
                            filename = filenamePair.getValue();
                            break;
                        }
                    }
                }
                Pattern pattern = Pattern.compile("(.+)''(.+)");
                Matcher matcher = pattern.matcher(filename);
                if (matcher.matches()) {
                    filename = matcher.group(2);
                    filename = URLDecoder.decode(filename, matcher.group(1));
                }
                Map map = new HashMap();
                map.put("filename", filename);
                map.put("mime", response.getEntity().getContentType().getValue());
                copyAndClose(response.getEntity().getContent(), out);
                return map;
            }
        } finally {
            close(response);
            if (request != null) {
                request.reset();
            }
            close(httpClient);
        }
    }

    private static void copyAndClose(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) {
            return;
        }
        BufferedInputStream bin = null;
        BufferedOutputStream bout = null;
        try {
            bin = new BufferedInputStream(in);
            bout = new BufferedOutputStream(out);
            while(true) {
                int b = bin.read();
                if (b == -1) {
                    break;
                } else {
                    bout.write(b);
                }
            }
        } finally {
            close(bin);
            close(bout);
            close(in);
            close(out);
        }
    }
    private static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch(Exception ignore) {}
    }

    public PoiClient(String baseAddress, String username, String password) throws MalformedURLException {
        this.baseUrl = new URL(baseAddress.endsWith("/")? baseAddress: baseAddress + "/");
        this.authentication = Base64.encodeBase64String((username + ":" + password).getBytes());
    }
}
