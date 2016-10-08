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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class HttpHelper {
    private HttpHelper() {}
    
    /**
     * Make an HTTP request which returns a redirect and get the target url.
     * @param method  HTTP method (GET, POST etc.)
     * @param url  the URL of the service
     * @return
     * @throws IOException
     */
    public static String requestRedirect(final String method, final String url) throws IOException {
        URLConnection urlConnection = new URL(url).openConnection();
        if (!(urlConnection instanceof HttpURLConnection)) {
           throw new IOException("URL did not lead to a http connection: " + url);
        }
        
        HttpURLConnection conn = (HttpURLConnection) urlConnection;
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(false);
        conn.connect();
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 303) {
            throw new IOException("Server did not respond with a redirect: " +
                    responseCode + " " + conn.getResponseMessage());
        }
        
        String redirectTo = conn.getHeaderField("Location");
        if (redirectTo == null) {
            throw new IOException("Redirection target not set in HTTP header.");
        }
        
        return redirectTo;
    }

}
