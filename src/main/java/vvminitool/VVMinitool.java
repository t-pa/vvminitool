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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.prefs.Preferences;

/**
 * Simple command-line interface to the VVService class.
 */
public class VVMinitool {
    
    private static void printUsage() {
        System.out.println("Usage: java -jar vvminitool.jar [command], " +
                "where  [command] is one of these:");
        System.out.println("  init               initialize a new process and show hash value");
        System.out.println("  status             show process status and hash value");
        System.out.println("  auth eid           select eid authentication method");
        System.out.println("  eid                perform eid authentication process");
        System.out.println("  email [address]    set e-mail address");
        System.out.println("  validate [code]    submit e-mail validation code");
        System.out.println("  showdata           show personal data");
        System.out.println("  csr [type] [file]  upload a certificate signing request");
        System.out.println("                     type is either 'sign', 'auth', or 'encr'");
        System.out.println("  csrdone            tell server all CSRs have been uploaded");
        System.out.println("  getcert [type] [file]  download the signed certificate and save it to file");
        System.out.println("                     type is either 'sign', 'auth', or 'encr'");
        System.out.println("  finalize           end the current process");        
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("Volksverschlüsselung minitool");
            System.out.println();
            printUsage();
            System.exit(0);
        }
        
        String command = args[0];

        VVService vvService = new VVService();
        Preferences prefs = Preferences.userNodeForPackage(VVMinitool.class);

        try {
            String processId = prefs.get("processId", "");
            if (processId.equals("") && !(command.equals("init") && args.length == 1)) {
               throw new IOException("Could not load process id. Call init command.");
            }
            vvService.setProcessId(processId);

            // init
            if (command.equals("init") && args.length == 1) {
                System.out.println("Initializing a new process...");
                vvService.initProcess();
                prefs.put("processId", vvService.getProcessId());
                System.out.println("Process id has been saved. Hash: ");
                System.out.println(vvService.getProcessHash());
            } 
            // auth [method]
            else if (command.equals("auth") && args.length == 2) {
                System.out.println("Setting authentication method " + args[1] + "... ");
                vvService.selectAuthMethod(args[1]);
            }
            // eid
            else if (command.equals("eid") && args.length == 1) {
                String eidSession = vvService.initEidSession();
                System.out.println("Starting authentication...");
                String authCode = vvService.performEidAuthentication(eidSession);
                vvService.confirmEidSession(eidSession, authCode);
                System.out.println("Authentication successful.");
            }
            // email [address]
            else if (command.equals("email") && args.length == 2) {
                System.out.println("Submitting e-mail address '" + args[1] + "'...");
                vvService.submitEmailAddress(args[1]);
            }
            // validate [code]
            else if (command.equals("validate") && args.length == 2) {
                System.out.println("Submitting validation code '" + args[1] + "'...");
                vvService.validateEmailAddress(args[1]);
            }
            // showdata
            else if (command.equals("showdata") && args.length == 1) {
                System.out.println("Requesting personal data...");
                String persData = vvService.requestPersonalData();
                System.out.println(persData);
            }
            // csr [type] [file]
            else if (command.equals("csr") && args.length == 3) {
                byte[] csrData = Files.readAllBytes(Paths.get(args[2]));
                System.out.println("Sending CSR of type '" + args[1] + "'...");
                vvService.uploadCSR(args[1], csrData);
            }
            // csrdone
            else if (command.equals("csrdone") && args.length == 1) {
                vvService.uploadFinished();
            }
            // getcert [type] [file]
            else if (command.equals("getcert") && args.length == 3) {
                Path certFile = Paths.get(args[2]);
                if (Files.exists(certFile)) {
                    throw new IOException("File '" + args[2] + "' already exists.");
                }
                System.out.println("Requesting signed certificate of type '" + args[1] + "'...");
                byte[] cert = vvService.downloadCert(args[1]);
                Files.write(certFile, cert);
            }
            // finalize
            else if (command.equals("finalize") && args.length == 1) {
                prefs.put("processId", "");
                vvService.finalizeProcess();
                System.out.println("Process finalized.");
            }
            // status
            else if (command.equals("status") && args.length == 1) {
                System.out.println("Process id hash: ");
                System.out.println(vvService.getProcessHash());
                // status will be printed at the end
            }
            else {
                System.out.println("Unknown command or wrong number of parameters.");
                printUsage();
                System.exit(1);
            }

            if (! "".equals(vvService.getProcessId())) {
                System.out.println("process status: " + vvService.processStatus());
            }
        } catch (IOException | NoSuchAlgorithmException | IllegalStateException ex) {
            System.err.println(ex);
        }
    }
    
}