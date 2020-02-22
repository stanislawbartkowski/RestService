package com.rest.restservice;

import java.util.logging.Logger;

public class RestLogger {

    public static Logger L = Logger.getLogger("com.rest.restservice.RestHelper");

    public static void info(String s) {
        L.info(s);
    }

    public static void debug(String s) {
//        L.log(Level.FINE,s);
        info(s);
    }
}
