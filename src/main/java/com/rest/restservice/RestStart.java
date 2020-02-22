package com.rest.restservice;

import com.sun.net.httpserver.HttpServer;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

abstract public class RestStart {

    static protected void RestStart(int PORT, Consumer<HttpServer> registerServices) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        RestLogger.info("Start HTTP Server, listening on port " + PORT);
        registerServices.accept(server);
        server.setExecutor(null); // creates a default executor
        server.start();
    }
}
