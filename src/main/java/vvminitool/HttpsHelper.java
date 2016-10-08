/*
 * Copyright 2016 t-pa
 *
 * This file is part of vvminitool.
 *
 * vvminitool is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package vvminitool;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Helper class for HTTPS requests.
 */
public class HttpsHelper {
    
    private SSLSocketFactory sslSocketFactory;

    /**
     * Load a key store with trusted SSL certificates that will be used for all subsequent HTTPS connections.
     * @param keyStoreURL  URL to the key store to load
     * @param password  password to verify the integrity of the key store, or null if no check is required
     * @throws IOException
     * @throws KeyStoreException 
     */
    public void loadTrustStore(final URL keyStoreURL, final char[] password) throws IOException, KeyStoreException {
        try (final InputStream input = keyStoreURL.openStream()) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(input, password);
            if (!trustStore.aliases().hasMoreElements()){
                throw new KeyStoreException("Key store is empty.");
            }

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            sslSocketFactory = sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | CertificateException | KeyManagementException ex) {
            throw new KeyStoreException("Error loading trust store", ex);
        }
    }

    /**
     * Make a request to an HTTPS service and return the answer as a String.
     * @param method
     * @param url
     * @return  a String which contains the answer of the server
     * @throws IOException 
     */
    public String request(final String method, final String url) throws IOException {
        return request(method, url, null, null);
    }

    /** 
     * Make a request to an HTTPS service and return the answer as a String.
     * @param method  HTTP method (GET, POST etc.)
     * @param url  the URL of the service
     * @param formDataName  name of the formData; may be null if formData is null
     * @param formData  send this data to the server
     * @return  a String which contains the answer of the server
     * @throws IllegalStateException  if the server returns code 409
     * @throws IOException 
     */
    public String request(final String method, final String url, 
            final String formDataName, final byte[] formData) throws IOException {
        
        URLConnection urlConnection = new URL(url).openConnection();
        if (!(urlConnection instanceof HttpsURLConnection)) {
           throw new IOException("URL did not lead to a https connection: " + url);
        }
        
        HttpsURLConnection conn = (HttpsURLConnection) urlConnection;
        if (sslSocketFactory != null) {
            conn.setSSLSocketFactory(sslSocketFactory);
        }
        conn.setRequestMethod(method);

        // prepare data to send
        byte[] outputData = null;
        if (formData != null) {
            final String BOUNDARY = "---boundary6dfb03cc-5d1a-4700-a0f1-203dbdb9f2ea---";
            
            ByteArrayOutputStream byteArr = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(byteArr, "UTF-8");
            writer.write("--" + BOUNDARY + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"" + formDataName + "\"; filename=\"" + formDataName + "\"\r\n");
            writer.write("Content-Type: application/octet-stream\r\n\r\n");
            writer.flush();
            byteArr.write(formData);
            writer.write("\r\n--" + BOUNDARY + "--\r\n");
            writer.close();
            outputData = byteArr.toByteArray();

            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setRequestProperty("Content-Length", String.valueOf(outputData.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(outputData);
        }
        
        conn.connect();
        int responseCode = conn.getResponseCode();
        if (responseCode == 409) {
            throw new IllegalStateException("This operation is not possible " +
                    "in the current context (HTTP response 409).");
        }
        
        // receive result
        StringBuilder result = new StringBuilder();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
        }
        
        return result.toString();
    }
    
}
