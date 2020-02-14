package com.rest.restservice;

import java.util.logging.Logger;

class RestLogger {
    static Logger L = Logger.getLogger("com.rest.restservice.RestHelper");

    static void info(String s) {
        L.info(s);
    }

    static void debug(String s) {
//        L.log(Level.FINE,s);
        info(s);
    }
}
