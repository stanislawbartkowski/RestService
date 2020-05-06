package com.rest.restservice.ssl;

import com.rest.restservice.RestLogger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.logging.Level;

public class SecureHttp {

    private static SSLContext load(String keystoreFilename, String keypassword, String storepassword, String alias) throws CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
        // load certificate
        char[] storepass = storepassword.toCharArray();
        char[] keypass = keypassword.toCharArray();
        FileInputStream fIn = new FileInputStream(keystoreFilename);
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(fIn, storepass);
// display certificate
        Certificate cert = keystore.getCertificate(alias);
        RestLogger.info(cert.toString());
// setup the key manager factory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keystore, keypass);
        SSLContext sslContext = SSLContext.getInstance("TLSv1");
        sslContext.init(kmf.getKeyManagers(), null, null);
        // SSLContext.getDefault();
        return sslContext;
    }

    public static HttpServer produceHttps(int PORT, String[] params) throws IOException {
        HttpsServer server = HttpsServer.create();
        // create https server
        server = HttpsServer.create(new InetSocketAddress(PORT), 0);
// create ssl context
        SSLContext sslContext = null;
        try {
            sslContext = load(params[0], params[1], params[2], params[3]);
        } catch (CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | KeyManagementException e) {
            String mess = "Cannot initialize SSL context";
            RestLogger.L.log(Level.SEVERE, mess, e);
            throw new IOException(mess, e);
        }
// setup the HTTPS context and parameters
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            public void configure(HttpsParameters params) {
                // initialise the SSL context
                SSLContext c = getSSLContext();
                SSLEngine engine = c.createSSLEngine();
                params.setNeedClientAuth(false);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                params.setProtocols(engine.getEnabledProtocols());
                // get the default parameters
//                SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                SSLParameters defaultSSLParameters = c.getSupportedSSLParameters();
                params.setSSLParameters(defaultSSLParameters);
            }
        });
        return server;
    }
}
