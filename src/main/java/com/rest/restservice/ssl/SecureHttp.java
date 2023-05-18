package com.rest.restservice.ssl;

/*
 * Copyright 2023 stanislawbartkowski@gmail.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.rest.restservice.RestLogger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.logging.Level;

public class SecureHttp {

    private static SSLContext load(String keystoreFilename, String password) throws CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
        // load certificate
        char[] storepass = password.toCharArray();
        char[] keypass = password.toCharArray();
        FileInputStream fIn = new FileInputStream(keystoreFilename);
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(fIn, storepass);
// display certificate
//        String alias = "alias";
//        Certificate cert = keystore.getCertificate(alias);
//        RestLogger.info(cert.toString());
// setup the key manager factory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keystore, keypass);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        // 2021/03/10
        // Important: keep TLS and below: Otherwise, it wil hang after first call
        // SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keystore);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        // SSLContext.getDefault();
        return sslContext;
    }

    public static HttpsServer produceHttps(int PORT, String[] params) throws IOException {
        // create https server
        HttpsServer server = HttpsServer.create(new InetSocketAddress(PORT), 0);
// create ssl context
        SSLContext sslContext = null;
        try {
            sslContext = load(params[0], params[1]);
        } catch (CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | KeyManagementException e) {
            String mess = "Cannot initialize SSL context";
            RestLogger.L.log(Level.SEVERE, mess, e);
            throw new IOException(mess, e);
        }
// setup the HTTPS context and parameters
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            public void configure(HttpsParameters params) {
                // initialise the SSL context
  //              SSLContext c = getSSLContext();
                SSLContext c = null;
                try {
                    c = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                SSLEngine engine = c.createSSLEngine();
                params.setNeedClientAuth(false);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                params.setProtocols(engine.getEnabledProtocols());
                // get the default parameters
                SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
//                SSLParameters defaultSSLParameters = c.getSupportedSSLParameters();
                params.setSSLParameters(defaultSSLParameters);
            }
        });
        return server;
    }
}
