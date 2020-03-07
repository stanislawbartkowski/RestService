package com.rest.restservice;

/*
 * Copyright 2020 stanislawbartkowski@gmail.com
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
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Main helper class starting HTTP server. Utilizes standard com.sun.net.httpserver.HttpServer server.
 */
abstract public class RestStart {


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
// setup the trust manager factory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keystore);
        SSLContext sslContext = SSLContext.getInstance("TLSv1");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }


    private static HttpServer produce(int PORT, String[] params) throws IOException {
        if (params.length == 0) return HttpServer.create(new InetSocketAddress(PORT), 0);
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
                SSLContext c = null;
                try {
                    c = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    RestLogger.L.log(Level.SEVERE, "Cannot start SSL Context", e);
                }
                SSLEngine engine = c.createSSLEngine();
                params.setNeedClientAuth(false);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                params.setProtocols(engine.getEnabledProtocols());
                // get the default parameters
                SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                params.setSSLParameters(defaultSSLParameters);
            }
        });
        return server;
    }

    /**
     * Starts HTTP server
     *
     * @param PORT             TCP/IP port the server is listening
     * @param registerServices Consumer class to register REST services.
     * @param params           Parameters for secure connection,
     *                         if zero parameter : non-secure connection HTTP
     *                         if more than zero: secure connection
     *                         params[0] = keystorefilename
     *                         params[1] = keypassword
     *                         params[2] = storepassword
     *                         params[3] = alias
     * @throws IOException
     */
    static protected void RestStart(int PORT, Consumer<HttpServer> registerServices, String[] params) throws IOException {
        HttpServer server = produce(PORT,params);
        RestLogger.info("Start HTTP Server, listening on port " + PORT);
        registerServices.accept(server);
        server.setExecutor(null); // creates a default executor
        server.start();
    }
}
