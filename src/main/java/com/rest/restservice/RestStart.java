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


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

/**
 * Main helper class starting HTTP server. Utilizes standard com.sun.net.httpserver.HttpServer server.
 */
abstract public class RestStart {

    /**
     * Starts HTTP server
     * @param PORT TCP/IP port the server is listening
     * @param registerServices Consumer class to register REST services.
     * @throws IOException
     */
    static protected void RestStart(int PORT, Consumer<HttpServer> registerServices) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        RestLogger.info("Start HTTP Server, listening on port " + PORT);
        registerServices.accept(server);
        server.setExecutor(null); // creates a default executor
        server.start();
    }
}
