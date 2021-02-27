package com.rest.restservice;

/*
 * Copyright 2021 stanislawbartkowski@gmail.com
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

import com.rest.restservice.kerberos.HttpNegotiateServer;
import com.rest.restservice.ssl.SecureHttp;
import com.sun.net.httpserver.*;

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

    private static HttpServer produce(int PORT, String[] params) throws IOException {
        return params.length == 0 ? HttpServer.create(new InetSocketAddress(PORT), 0) :
                SecureHttp.produceHttps(PORT,params);
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


    static protected void RestStart(int PORT, Consumer<HttpServer> registerServices, String[] params) throws Exception {
        HttpServer server = produce(PORT,params);

        if (System.getProperty("java.security.auth.login.config") != null)
            RestHelper.setAuth(HttpNegotiateServer.constructNegotiateAuthenticator());

        RestLogger.info("Start HTTP Server, listening on port " + PORT);
        registerServices.accept(server);

        server.setExecutor(null); // creates a default executor
        server.start();
    }
}
