package com.pamir.dump.cases.utils;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

//https://mkyong.com/java/apache-httpclient-examples/
public class WebPageLoader {
    public void load() throws IOException {

        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {

            HttpGet request = new HttpGet("https://www.microsoft.com");

            CloseableHttpResponse response = httpClient.execute(request);

            try {

                // Get HttpResponse Status
                //System.out.println(response.getProtocolVersion()); // HTTP/1.1
                //System.out.println(response.getStatusLine().getStatusCode()); // 200
                //System.out.println(response.getStatusLine().getReasonPhrase()); // OK
                //System.out.println(response.getStatusLine().toString()); // HTTP/1.1 200 OK

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // return it as a String
                    String result = EntityUtils.toString(entity);
                }

            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }
}