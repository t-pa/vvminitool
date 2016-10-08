# Volksverschluesselung minitool README

The vvminitool allows you to use the [Volksverschluesselung](https://volksverschluesselung.de)
service with Linux or any other operating system with Java support (including Windows).
It is free software licensed under the [GPL](https://www.gnu.org/licenses/gpl-3.0.en.html)). Its author is not affiliated with the Volksverschluesselung
service and the tool is not officially supported.

Features:

- simple tool to use the [Volksverschluesselung](https://volksverschluesselung.de) web service
- secure by design: never touches your private keys
- runs on Linux and on any OS with Java support (authentication requires [openecard](https://www.openecard.org))

## What does the Volksverschluesselung service do?
The Volksverschluesselung service is a certification authority for S/MIME email
certificates. It checks your identity (usually you need an electronic German
Identity Card ("Neuer Personalausweis") for this step) and then fulfills your
X.509 certificate signing request. For an [English self-description go here](https://www.sit.fraunhofer.de/en/volksverschluesselung/).


## How to get your certificates signed by the Volksverschluesselung service

### Authenticate yourself and validate your email address
Initialize a new process.


    user@swtest:~/vvtest$ java -jar vvminitool.jar init
    Initializing a new process...
    Process id has been saved. Hash: 
    00yourhash000000000000000000000000000000000000000000000000000000
    process status: AUTH_PENDING

Authenticate yourself. The openecard application must be running.

    user@swtest:~/vvtest$ java -jar vvminitool.jar auth eid
    Setting authentication method eid... 
    process status: AUTH_EID_READY
    user@swtest:~/vvtest$ java -jar vvminitool.jar eid
    Starting authentication...
      [follow instructions from openecard application]
    process status: EMAIL_PENDING

Set your email address.

    user@swtest:~/vvtest$ java -jar vvminitool.jar email mail@example.com
    Submitting e-mail address 'mail@example.com'...
    process status: EMAIL_NOT_VALIDATED


You should receive a validation code via email. Verify your email address
by sending it.

    user@swtest:~/vvtest$ java -jar vvminitool.jar validate AB123
    Submitting validation code 'AB123'...
    process status: AUTH_COMPLETED


Check your personal data that will be used in the certificate.

    user@swtest:~/vvtest$ java -jar vvminitool.jar showdata
    Requesting personal data...
    {"Result":{"ErrorCode":"GENERIC_SUCCESS","ProcessStatus":"CERT_AWAIT_REQUEST"},"Data":{"UserIdentity":{"GivenNames":"Firstname","FamilyNames":"Familyname","AcademicTitle":null},"EmailAddress":"mail@example.com"}}
    process status: CERT_AWAIT_REQUEST


### Create S/MIME key pairs and certificate signing requests (CSRs)
Use openssl to generate three RSA keys for signing, encryption, and
authentification, respectively. In this step, your private keys
are saved to a file without encryption; if you do not use an encrypted file
system, you can encrypt the key with the `-aes256` option.

    user@swtest:~/vvtest$ openssl genrsa -out key_sign.pem 2048
    Generating RSA private key, 2048 bit long modulus
    ....................................................................................+++
    ...+++
    e is 65537 (0x10001)
    user@swtest:~/vvtest$ openssl genrsa -out key_encr.pem 2048
    Generating RSA private key, 2048 bit long modulus
    .......................................+++
    ...........................................................................................+++
    e is 65537 (0x10001)
    user@swtest:~/vvtest$ openssl genrsa -out key_auth.pem 2048
    Generating RSA private key, 2048 bit long modulus
    ......................................................+++
    .....+++
    e is 65537 (0x10001)


Generate certificate signing requests. Replace the hash value with the one you
got in the first step; you can also retrieve it with `java -jar vvminitool.jar status`.

    user@swtest:~/vvtest$ openssl req -outform DER -new -subj /CN=00yourhash000000000000000000000000000000000000000000000000000000/ -key key_sign.pem -out csr_sign.pem
    user@swtest:~/vvtest$ openssl req -outform DER -new -subj /CN=00yourhash000000000000000000000000000000000000000000000000000000/ -key key_encr.pem -out csr_encr.pem
    user@swtest:~/vvtest$ openssl req -outform DER -new -subj /CN=00yourhash000000000000000000000000000000000000000000000000000000/ -key key_auth.pem -out csr_auth.pem


### Send CSRs to the Volksverschluesselung service and retrieve certificates

Send your certificate signing requests.

    user@swtest:~/vvtest$ java -jar vvminitool.jar csr sign csr_sign.pem
    Sending CSR of type 'sign'...
    process status: CERT_AWAIT_REQUEST
    user@swtest:~/vvtest$ java -jar vvminitool.jar csr encr csr_encr.pem 
    Sending CSR of type 'encr'...
    process status: CERT_AWAIT_REQUEST
    user@swtest:~/vvtest$ java -jar vvminitool.jar csr auth csr_auth.pem 
    Sending CSR of type 'auth'...
    process status: CERT_AWAIT_REQUEST


Complete the upload.

    user@swtest:~/vvtest$ java -jar vvminitool.jar csrdone
    process status: CERT_IN_PROGRESS


Wait until the status changes to CERT_AVAILABLE. You will also get a revocation
password by email when the certificates are ready.

    user@swtest:~/vvtest$ java -jar vvminitool.jar status
    Process id hash: 
    00yourhash000000000000000000000000000000000000000000000000000000
    process status: CERT_IN_PROGRESS
    user@swtest:~/vvtest$ java -jar vvminitool.jar status
    Process id hash: 
    00yourhash000000000000000000000000000000000000000000000000000000
    process status: CERT_AVAILABLE


Download certificates and finalize process:

    user@swtest:~/vvtest$ java -jar vvminitool.jar getcert sign cert_sign.der
    Requesting signed certificate of type 'sign'...
    process status: CERT_AVAILABLE
    user@swtest:~/vvtest$ java -jar vvminitool.jar getcert encr cert_encr.der
    Requesting signed certificate of type 'encr'...
    process status: CERT_AVAILABLE
    user@swtest:~/vvtest$ java -jar vvminitool.jar getcert auth cert_auth.der
    Requesting signed certificate of type 'auth'...
    process status: CERT_AVAILABLE
    user@swtest:~/vvtest$ java -jar vvminitool.jar finalize


### Assemble key pairs for use in your e-mail client

Combine the certificates and the private keys into PKCS12 files.

    user@swtest:~/vvtest$ openssl x509 -inform DER -outform PEM -in cert_sign.der -out cert_sign.pem
    user@swtest:~/vvtest$ openssl x509 -inform DER -outform PEM -in cert_encr.der -out cert_encr.pem
    user@swtest:~/vvtest$ openssl x509 -inform DER -outform PEM -in cert_auth.der -out cert_auth.pem
    user@swtest:~/vvtest$ openssl pkcs12 -export -in cert_sign.pem -inkey key_sign.pem -out key_cert_sign.p12
    Enter Export Password:
    Verifying - Enter Export Password:
    user@swtest:~/vvtest$ openssl pkcs12 -export -in cert_encr.pem -inkey key_encr.pem -out key_cert_encr.p12
    Enter Export Password:
    Verifying - Enter Export Password:
    user@swtest:~/vvtest$ openssl pkcs12 -export -in cert_auth.pem -inkey key_auth.pem -out key_cert_auth.p12
    Enter Export Password:
    Verifying - Enter Export Password:

You can now import the PKCS12 files into your favorite email client.
