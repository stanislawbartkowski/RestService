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
import java.util.Optional;
import java.util.logging.Level;


public class RestHelper {

    private static final String RESTPREFIX = "/rest/";

    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String OPTIONS = "OPTIONS";

    private static final String TOKEN = "Token";
    private static final String AUTHORIZATION = "Authorization";

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

    public interface IQeuryInterface {
        Map<String, ParamValue> getValues();
    }

    private static class QueryInterface implements IQeuryInterface {

        final Map<String, ParamValue> values = new HashMap<String, ParamValue>();

        @Override
        public Map<String, ParamValue> getValues() {
            return values;
        }
    }

    abstract public static class RestServiceHelper implements HttpHandler {
        final String url;
        final String expectedMethod;
        final boolean tokenexpected;
        final Map<String, RestParam> params = new HashMap<String, RestParam>();

        public abstract void servicehandle(HttpExchange httpExchange, IQeuryInterface v) throws IOException;

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            Optional<IQeuryInterface> v = verifyURL(httpExchange);
            if (!v.isPresent()) return;
            try {
                servicehandle(httpExchange, v.get());
            } catch (Exception e) {
                RestLogger.L.log(Level.SEVERE, "Error while handling service", e);
                throw e;
            }
        }

        protected RestServiceHelper(String url, String expectedMethod, boolean tokenexpected) {
            this.url = url;
            this.expectedMethod = expectedMethod;
            this.tokenexpected = tokenexpected;
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

        private void addTokenHeader(HttpExchange t, String token) {
            t.getResponseHeaders().set(AUTHORIZATION, TOKEN + " " + token);
        }

        protected void produceResponse(HttpExchange t, Optional<String> message, int HTTPResponse, Optional<String> token) throws IOException {
            addCORSHeader(t);
            if (token.isPresent()) addTokenHeader(t, token.get());
            if ((!message.isPresent()) || message.get().equals("")) t.sendResponseHeaders(HTTPNODATA, 0);
            else {
                byte[] response = message.get().getBytes();
                t.sendResponseHeaders(HTTPResponse, response.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(response);
                }
            }
        }

        protected void produceResponse(HttpExchange t, Optional<String> message, int HTTPResponse) throws IOException {
            produceResponse(t, message, HTTPResponse, Optional.empty());
        }

        protected void produceParameterNotFound(HttpExchange t, String s) throws IOException {
            produceResponse(t, Optional.of("Parameter " + s + " not found in url"), HTTPBADREQUEST);
        }


        protected void produceOKResponse(HttpExchange t, Optional<String> message, Optional<String> token) throws IOException {
            produceResponse(t, message, HTTPOK, token);
        }

        protected void produceOKResponse(HttpExchange t, Optional<String> message) throws IOException {
            produceOKResponse(t, message, Optional.empty());
        }

        protected void produceOKResponse(HttpExchange t, String message) throws IOException {
            if (message == null || message.equals("")) produceNODATAResponse(t);
            else produceOKResponse(t, Optional.of(message), Optional.empty());
        }

        protected void produceNODATAResponse(HttpExchange t) throws IOException {
            produceResponse(t, Optional.empty(), HTTPNODATA);
        }

        private boolean verifyMethod(HttpExchange t) throws IOException {

            if (OPTIONS.equals(t.getRequestMethod()) || expectedMethod.equals(t.getRequestMethod())) return true;
            String message = expectedMethod + " method expected, " + t.getRequestMethod() + " provided.";
            produceResponse(t, Optional.of(message), HTTPMETHODNOTALLOWED);
            return false;
        }

        protected Optional<String> getAuthorizationToken(HttpExchange t) {
            List<String> auth = t.getRequestHeaders().get(AUTHORIZATION);
            if (auth == null || auth.isEmpty()) return Optional.empty();
            for (String s : auth) {
                String[] e = s.split(" ");
                if (e.length > 1 && e[0].equals(TOKEN)) return Optional.of(e[1]);
            }
            return Optional.empty();
        }

        protected Optional<String> getAuthorizationToken(HttpExchange t, boolean expected) throws IOException {
            Optional<String> token = getAuthorizationToken(t);
            if ((!token.isPresent() || token.get().equals("")) && expected) {
                produceResponse(t, Optional.of("Authorization token is expected."), HTTPBADREQUEST);
                return Optional.empty();
            }
            return token;
        }


        private Optional<IQeuryInterface> verifyURL(HttpExchange t) throws IOException {
            RestLogger.debug(t.getRequestMethod() + " " + t.getRequestURI().getQuery());
            if (!verifyMethod(t)) return Optional.empty();
            if (tokenexpected && !getAuthorizationToken(t, tokenexpected).isPresent()) Optional.empty();
            // verify param
            // check if parameters allowed
            QueryInterface v = new QueryInterface();
            if (t.getRequestURI().getQuery() != null) {
                String qq = t.getRequestURI().getQuery();
                String query = URLDecoder.decode(qq, StandardCharsets.UTF_8.toString());
                String[] q = query.split("&");
                for (String qline : q) {
                    String[] vv = qline.split("=");
                    String s = vv[0];
                    String val = vv.length == 1 ? "" : vv[1];
                    if (!params.containsKey(s)) {
                        produceResponse(t, Optional.of("Parameter " + s + " not expected."), HTTPBADREQUEST);
                        return Optional.empty();
                    }
                    // get value
                    RestParam rpara = params.get(s);
                    switch (rpara.ptype) {
                        case BOOLEAN: {
                            if (val.equals("true") || val.equals("false")) {
                                v.values.put(s, new ParamValue(val.equals("true")));
                                break;
                            }
                            // incorrect true or false
                            produceResponse(t, Optional.of("Parameter " + s + "?" + val + " true or false expected"), HTTPBADREQUEST);
                            return Optional.empty();
                        }
                        case INT: {
                            try {
                                int i = Integer.parseInt(val);
                                v.values.put(s, new ParamValue(i));
                                break;
                            } catch (NumberFormatException e) {
                                produceResponse(t, Optional.of("Parameter " + s + "?" + val + " incorrect integer value"), HTTPBADREQUEST);
                                Optional.empty();
                            }
                        }
                        case STRING: {
                            v.values.put(s, new ParamValue(val));
                            break;
                        }
                    }
                } // for
            }
            // verify obligatory params
            for (String s : params.keySet()) {
                if (!v.values.containsKey(s)) {
                    if (params.get(s).obligatory) {
                        produceParameterNotFound(t, s);
                        return Optional.empty();
                    }
                    // set default value
                    v.values.put(s, params.get(s).defa);
                }
            }

            if (OPTIONS.equals(t.getRequestMethod())) {
                RestLogger.L.info(OPTIONS + " request");
                produceOKResponse(t, Optional.of("OK"));
                // return false, to avoid sending the content for OPTIONS
                Optional.empty();
            }
            return Optional.of(v);
        }

        protected boolean getLogParam(IQeuryInterface v, String param) {
            return v.getValues().get(param).logvalue;
        }

        protected int getIntParam(IQeuryInterface v, String param) {
            return v.getValues().get(param).intvalue;
        }

        protected String getStringParam(IQeuryInterface v, String param) {
            return v.getValues().get(param).stringvalue;
        }

        protected Optional<String> getStringParamExpected(HttpExchange t, IQeuryInterface v, String param) throws IOException {
            String val = getStringParam(v, param);
            if (val == null || val.equals("")) {
                produceParameterNotFound(t, param);
                return Optional.empty();
            }
            return Optional.of(val);
        }
    }

    public static void registerService(HttpServer server, RestServiceHelper service) {
        RestLogger.info("Register service: " + service.url);
        server.createContext(RESTPREFIX + service.url, service);
    }

}
