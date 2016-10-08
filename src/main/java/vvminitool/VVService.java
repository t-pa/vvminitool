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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Interface to the Volksverschluesselung web service. The state of this class
 * stores the current process id.
 */
public class VVService {
    public final static String VVSERVER = "https://ra.volksverschluesselung.de";

    private HttpsHelper https = new HttpsHelper();
    private String processId = "";

    public VVService() {
        try {
            /* Load the SSL certificate for connections with the VV server. The key store containing
            * the certificate has been created as follows:
            * user@swtest:~$ wget https://volksverschluesselung.de/cert/VV-Root-CA.pem
            * user@swtest:~$ keytool -keystore vv-root-ca.cer -import -alias VV-Root-CA -file VV-Root-CA.pem
            */
            https.loadTrustStore(System.class.getResource("/vv-root-ca.cer"), "changeit".toCharArray());
        } catch (IOException | KeyStoreException ex) {
            Logger.getLogger(VVService.class.getName()).log(Level.WARNING, 
                    "Unable to load SSL certificates", ex);
        }
    }

    /**
     * Get the status of the Volksverschluesselung service.
     * @return the reply of the service
     * @throws IOException 
     */
    public String serviceStatus() throws IOException {
        return https.request("GET", VVSERVER + "/status/");
    }
    
    /**
     * Start a new process, requesting a new processId. The processId can be
     * stored (get it with getProcessId) and restored (setProcessId) to later
     * resume this process.
     * @throws IOException 
     */
    public void initProcess() throws IOException {
        String reply = https.request("POST", VVSERVER + "/process/");
        processId = new JSONObject(reply).getJSONObject("Data").getString("ProcessId");
    }
    
    /**
     * Get the status of the current process. Before calling this method, set
     * the processId or request a new processId (initProcess).
     * @return the current status of the process
     * @throws IOException
     */
    public String processStatus() throws IOException {
        String reply = https.request("GET", VVSERVER + "/process/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8"));
        return new JSONObject(reply).getJSONObject("Result").getString("ProcessStatus");
    }

    /**
     * Select an authentication method. Possible choices: eid, telekom, f2f,
     * micropy, postid
     * @param method
     * @throws IOException 
     */
    public void selectAuthMethod(String method) throws IOException {
        https.request("POST", VVSERVER + "/auth/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
            "&auth_type=" + URLEncoder.encode(method, "UTF-8"));
    }
    
    /**
     * Initialize an EID session.
     * @return the EID session code 
     * @throws java.io.IOException 
     */
    public String initEidSession() throws IOException {
        String reply = https.request("POST", VVSERVER + "/auth/eid/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8"));
        return new JSONObject(reply).getJSONObject("Data").getString("EIdSession");
    }

    /**
     * Perform the EID authentication. The openecard application must be running
     * and listening on 127.0.0.1:24727.
     * @param eidSession
     * @return the authentication code (if authentication is successful)
     * @throws IOException 
     */
    public String performEidAuthentication(String eidSession) throws IOException {
        String tokenURL = "https://ra.volksverschluesselung.de/auth/eid/?eid_session=" + URLEncoder.encode(eidSession, "UTF-8");
        String url = "http://127.0.0.1:24727/eID-Client?tcTokenURL=" + URLEncoder.encode(tokenURL, "UTF-8");

        String redirectTo;
        try {
            redirectTo = HttpHelper.requestRedirect("GET", url);
        } catch (IOException ex) {
            throw new IOException("Could not connect to local openecard application.", ex);
        }
        Matcher matcher = Pattern.compile("auth_key=([^&]+)").matcher(redirectTo);
        if (!matcher.find()) {
            throw new IllegalStateException("auth_key not found in redirection target");
        }
        String authKey = URLDecoder.decode(matcher.group(1), "UTF-8");
        return authKey;
    }

    /**
     * Confirm an EID session with an authentication code.
     * @param eidSession
     * @param authCode
     * @return the reply of the service
     * @throws IOException 
     */
    public String confirmEidSession(String eidSession, String authCode) throws IOException {
        return https.request("PUT", VVSERVER + "/auth/eid/" +
            "?eid_session=" + URLEncoder.encode(eidSession, "UTF-8") +
            "&eid_authkey=" + URLEncoder.encode(authCode, "UTF-8") +
            "&success=true");
    }

    /**
     * Submit the email address.
     * @param email
     * @throws IOException 
     */
    public void submitEmailAddress(String email) throws IOException {
        https.request("POST", VVSERVER + "/email/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
            "&email_addr=" + URLEncoder.encode(email, "UTF-8") +
            "&force_flag=false");
    }

    /**
     * Validate the email address.
     * @param validationCode
     * @throws IOException 
     */
    public void validateEmailAddress(String validationCode) throws IOException {
        https.request("PUT", VVSERVER + "/email/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
            "&validation_code=" + URLEncoder.encode(validationCode, "UTF-8"));
    }

    /**
     * Request the personal data that will be used within the certificates.
     * @return
     * @throws IOException 
     */
    public String requestPersonalData() throws IOException {
        return https.request("GET", VVSERVER + "/users/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8"));
    }
    /**
     * Upload a certificate signing request.
     * @param certType either "sign", "auth", or "encr"
     * @param csrData
     * @throws IOException 
     */
    public void uploadCSR(String certType, byte[] csrData) throws IOException {
        https.request("POST", VVSERVER + "/certificates/" +
                "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
                "&cert_type=" + URLEncoder.encode(certType, "UTF-8"),
            "certification_request", csrData);
    }
    
    /**
     * Tell the server all CSRs have been uploaded.
     * @throws IOException 
     */
    public void uploadFinished() throws IOException {
        https.request("PUT", VVSERVER + "/certificates/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
            "&publish=false");
    }
    
    /**
     * Download a signed certificate.
     * @param certType
     * @return
     * @throws IOException 
     */
    public byte[] downloadCert(String certType) throws IOException {
        String reply = https.request("GET", VVSERVER + "/certificates/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
            "&cert_type=" + URLEncoder.encode(certType, "UTF-8"));

        JSONObject json = new JSONObject(reply);
        String cert64 = json.getJSONObject("Data").getString("CertificateData");
        byte[] cert = Base64.getDecoder().decode(cert64);
        return cert;
    }

    /**
     * End the current process. This also resets the processId to ''.
     * @throws IOException 
     */
    public void finalizeProcess() throws IOException {
        https.request("DELETE", VVSERVER + "/process/" +
            "?process_id=" + URLEncoder.encode(processId, "UTF-8") +
            "&success=true");
        processId = "";
    }
    
    /**
     * Compute an SHA-256 hash and format it as a String; the
     * Volksverschluesselung service uses the same hash function.
     * The original implementation is called StringUtils.toHashString in the
     * Volksverschluesselung client software.
     * @param s
     * @return
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException 
     */
    public static String hexHash(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(s.getBytes("UTF-8"));
        byte[] hash = md.digest();
        
        return String.format("%064x", new java.math.BigInteger(1, hash));
    }

    public String getProcessHash() throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return hexHash(processId);
    }
    
    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = (processId == null) ? "" : processId;
    }
}
