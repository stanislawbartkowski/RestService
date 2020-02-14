package com.rest.restservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;


public class RestHelper {

    private static final String RESTPREFIX = "/rest/";

    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String OPTIONS = "OPTIONS";

    private static final int HTTPOK = 200;
    public static final int HTTPNODATA = 204;
    public static final int HTTPMETHODNOTALLOWED = 405;
    public static final int HTTPBADREQUEST = 400;


    public enum PARAMTYPE {
        BOOLEAN, INT, STRING
    }

    public static class ParamValue {
        final boolean logvalue;
        final int intvalue;
        final String stringvalue;

        ParamValue() {
            this.logvalue = false;
            this.intvalue = -1;
            this.stringvalue = null;
        }

        ParamValue(boolean logvalue) {
            this.logvalue = logvalue;
            this.intvalue = -1;
            this.stringvalue = null;
        }

        ParamValue(int intvalue) {
            this.intvalue = intvalue;
            this.logvalue = false;
            this.stringvalue = null;
        }

        public ParamValue(String stringvalue) {
            this.intvalue = -1;
            this.logvalue = false;
            this.stringvalue = stringvalue;
        }

    }

    public static class RestParam {
        final PARAMTYPE ptype;
        final boolean obligatory;
        final ParamValue defa;

        RestParam(PARAMTYPE ptype) {
            this.ptype = ptype;
            this.obligatory = true;
            defa = new ParamValue();
        }

        RestParam(PARAMTYPE ptype, ParamValue defa) {
            this.ptype = ptype;
            this.obligatory = false;
            this.defa = defa;
        }
    }

    abstract public static class RestServiceHelper implements HttpHandler {
        final String url;
        final String expectedMethod;
        final Map<String, RestParam> params = new HashMap<String, RestParam>();
        int successResponse;
        final Map<String, ParamValue> values = new HashMap<String, ParamValue>();

        public abstract void servicehandle(HttpExchange httpExchange) throws IOException;


        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            if (!verifyURL(httpExchange)) return;
            try {
                servicehandle(httpExchange);
            } catch (Exception e) {
                RestLogger.L.log(Level.SEVERE, "Error while handling service", e);
                throw e;
            }
        }

        protected RestServiceHelper(String url, String expectedMethod) {
            this.url = url;
            this.expectedMethod = expectedMethod;
        }

        protected void addParam(String paramName, PARAMTYPE ptype) {
            params.put(paramName, new RestParam(ptype));
        }

        protected void addParam(String paramName, PARAMTYPE ptype, ParamValue defa) {
            params.put(paramName, new RestParam(ptype, defa));
        }

        private void addCORSHeader(HttpExchange t) {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE,OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Access-Control-Allow-Headers, Origin,Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers,Authorization");
        }

        protected void produceResponse(HttpExchange t, String message, int HTTPResponse) throws IOException {
            addCORSHeader(t);
            if ((message == null) || message.equals("")) t.sendResponseHeaders(HTTPNODATA, 0);
            else {
                byte[] response = message.getBytes();
                t.sendResponseHeaders(HTTPResponse, response.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(response);
                }
            }
        }

        protected void produceOKResponse(HttpExchange t, String message) throws IOException {
            produceResponse(t, message, HTTPOK);
        }

        protected void produceNODATAResponse(HttpExchange t) throws IOException {
            produceResponse(t, "", HTTPNODATA);
        }

        private boolean verifyMethod(HttpExchange t) throws IOException {

            if (OPTIONS.equals(t.getRequestMethod()) || expectedMethod.equals(t.getRequestMethod())) return true;
            String message = expectedMethod + " method expected, " + t.getRequestMethod() + " provided.";
            produceResponse(t, message, HTTPMETHODNOTALLOWED);
            return false;
        }

        protected boolean verifyURL(HttpExchange t) throws IOException {
            RestLogger.debug(t.getRequestMethod() + " " + t.getRequestURI().getQuery());
            if (!verifyMethod(t)) return false;
            // verify param
            // check if parameters allowed
            values.clear();
            if (t.getRequestURI().getQuery() != null) {
                String qq = t.getRequestURI().getQuery();
                String query = URLDecoder.decode(qq, StandardCharsets.UTF_8.toString());
                String[] q = query.split("&");
                for (String qline : q) {
                    String[] vv = qline.split("=");
                    String s = vv[0];
                    String val = vv.length == 1 ? "" : vv[1];
                    if (!params.containsKey(s)) {
                        produceResponse(t, "Parameter " + s + " not expected.", HTTPBADREQUEST);
                        return false;
                    }
                    // get value
                    RestParam rpara = params.get(s);
                    switch (rpara.ptype) {
                        case BOOLEAN: {
                            if (val.equals("true") || val.equals("false")) {
                                values.put(s, new ParamValue(val.equals("true")));
                                break;
                            }
                            // incorrect true or false
                            produceResponse(t, "Parameter " + s + "?" + val + " true or false expected", HTTPBADREQUEST);
                            return false;
                        }
                        case INT: {
                            try {
                                int i = Integer.parseInt(val);
                                values.put(s, new ParamValue(i));
                                break;
                            } catch (NumberFormatException e) {
                                produceResponse(t, "Parameter " + s + "?" + val + " incorrect integer value", HTTPBADREQUEST);
                                return false;
                            }
                        }
                        case STRING: {
                            values.put(s, new ParamValue(val));
                            break;
                        }
                    }
                } // for
            }
            // verify obligatory params
            for (String s : params.keySet()) {
                if (!values.containsKey(s)) {
                    if (params.get(s).obligatory) {
                        produceResponse(t, "Parameter " + s + " not found in url", HTTPBADREQUEST);
                        return false;
                    }
                    // set default value
                    values.put(s, params.get(s).defa);
                }
            }

            if (OPTIONS.equals(t.getRequestMethod())) {
                RestLogger.L.info(OPTIONS + " request");
                produceOKResponse(t, "OK");
                // return false, to avoid sending the content for OPTIONS
                return false;
            }
            return true;
        }

        protected  boolean getLogParam(String param) {
            return values.get(param).logvalue;
        }

        protected int getIntParam(String param) {
            return values.get(param).intvalue;
        }

        protected String getStringParam(String param) {
            return values.get(param).stringvalue;
        }
    }

    public static void registerService(HttpServer server, RestServiceHelper service) {
        RestLogger.info("Register service: " + service.url);
        server.createContext(RESTPREFIX + service.url, service);
    }

}
