package com.rest.restservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;


public class RestHelper {


    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String OPTIONS = "OPTIONS";

    private static final String TOKEN = "Token";
    private static final String AUTHORIZATION = "Authorization";

    private static final int HTTPOK = HttpURLConnection.HTTP_OK;
    public static final int HTTPNODATA = HttpURLConnection.HTTP_NO_CONTENT;
    public static final int HTTPMETHODNOTALLOWED = HttpURLConnection.HTTP_BAD_METHOD;
    public static final int HTTPBADREQUEST = HttpURLConnection.HTTP_BAD_REQUEST;

    // 'Content-Type: application/json'

    public interface IQueryInterface {
        Map<String, ParamValue> getValues();

        RestParams getRestParams();

        HttpExchange getT();
    }

    private static class QueryInterface implements IQueryInterface {

        private final Map<String, ParamValue> values = new HashMap<String, ParamValue>();
        private final RestParams pars;
        private final HttpExchange t;

        private QueryInterface(RestParams pars, HttpExchange t) {
            this.pars = pars;
            this.t = t;
        }

        @Override
        public Map<String, ParamValue> getValues() {
            return values;
        }

        @Override
        public RestParams getRestParams() {
            return pars;
        }

        @Override
        public HttpExchange getT() {
            return t;
        }

    }

    abstract public static class RestServiceHelper implements HttpHandler {
        final String url;
        final boolean tokenexpected;

        public abstract RestParams getParams(HttpExchange httpExchange) throws IOException;

        public abstract void servicehandle(IQueryInterface v) throws IOException;


        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {
                RestParams prest = getParams(httpExchange);
                Optional<IQueryInterface> v = verifyURL(httpExchange, prest);
                if (!v.isPresent()) return;
                servicehandle(v.get());
            } catch (Exception e) {
                RestLogger.L.log(Level.SEVERE, "Error while handling service", e);
                // create ad hoc class
                produceResponse(new IQueryInterface() {
                    @Override
                    public Map<String, ParamValue> getValues() {
                        return null;
                    }

                    @Override
                    public RestParams getRestParams() {
                        return new RestParams(GET, Optional.empty(),false, new ArrayList<String>());
                    }

                    @Override
                    public HttpExchange getT() {
                        return httpExchange;
                    }
                }, Optional.of(e.getMessage()), HTTPBADREQUEST);
            }
        }

        protected RestServiceHelper(String url, boolean tokenexpected) {
            this.url = url;
            this.tokenexpected = tokenexpected;
        }

        private void addCORSHeader(IQueryInterface v) {
            HttpExchange t = v.getT();
            RestParams pars = v.getRestParams();
            StringBuilder bui = new StringBuilder(OPTIONS);
            for (String m : pars.getMethodsAllowed()) bui.append(", " + m);
            t.getResponseHeaders().set("Access-Control-Allow-Methods", bui.toString());
            if (pars.isCrossedAllowed()) {
                t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                t.getResponseHeaders().set("Access-Control-Allow-Headers", "Access-Control-Allow-Headers, Origin,Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers,Authorization");
            }
            if (pars.getResponseCpntent().isPresent()) {
                switch (pars.getResponseCpntent().get()) {
                    case JSON:
                        t.getResponseHeaders().set("Content-Type", "application/json");
                        break;
                    case TEXT:
                        t.getResponseHeaders().set("Content-Type", "text/plain");
                        break;
                }
            }
            t.getResponseHeaders().set("charset", "utf8");
        }

        // 'Content-Type: application/json'
        private void addTokenHeader(IQueryInterface v, String token) {
            v.getT().getResponseHeaders().set(AUTHORIZATION, TOKEN + " " + token);
        }

        protected void produceResponse(IQueryInterface v, Optional<String> message, int HTTPResponse, Optional<String> token) throws IOException {
            addCORSHeader(v);
            if (token.isPresent()) addTokenHeader(v, token.get());
            HttpExchange t = v.getT();
            if ((!message.isPresent()) || message.get().equals("")) t.sendResponseHeaders(HTTPNODATA, 0);
            else {
                byte[] response = message.get().getBytes();
                t.sendResponseHeaders(HTTPResponse, response.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(response);
                }
            }
        }

        protected void produceResponse(IQueryInterface v, Optional<String> message, int HTTPResponse) throws IOException {
            produceResponse(v, message, HTTPResponse, Optional.empty());
        }

        protected void produceParameterNotFound(IQueryInterface v, String s) throws IOException {
            produceResponse(v, Optional.of("Parameter " + s + " not found in url"), HTTPBADREQUEST);
        }


        protected void produceOKResponse(IQueryInterface v, Optional<String> message, Optional<String> token) throws IOException {
            produceResponse(v, message, HTTPOK, token);
        }

        protected void produceOKResponse(IQueryInterface v, Optional<String> message) throws IOException {
            produceOKResponse(v, message, Optional.empty());
        }

        protected void produceOKResponse(IQueryInterface v, String message) throws IOException {
            if (message == null || message.equals("")) produceNODATAResponse(v);
            else produceOKResponse(v, Optional.of(message), Optional.empty());
        }

        protected void produceNODATAResponse(IQueryInterface v) throws IOException {
            produceResponse(v, Optional.empty(), HTTPNODATA);
        }

        private boolean verifyMethod(IQueryInterface v) throws IOException {

            HttpExchange t = v.getT();
            RestParams pars = v.getRestParams();
            if (OPTIONS.equals(t.getRequestMethod()) || pars.getRequestMethod().equals(t.getRequestMethod()))
                return true;
            String message = pars.getRequestMethod() + " method expected, " + t.getRequestMethod() + " provided.";
            produceResponse(v, Optional.of(message), HTTPMETHODNOTALLOWED);
            return false;
        }

        protected Optional<String> getAuthorizationToken(IQueryInterface v) {
            HttpExchange t = v.getT();
            List<String> auth = t.getRequestHeaders().get(AUTHORIZATION);
            if (auth == null || auth.isEmpty()) return Optional.empty();
            for (String s : auth) {
                String[] e = s.split(" ");
                if (e.length > 1 && e[0].equals(TOKEN)) return Optional.of(e[1]);
            }
            return Optional.empty();
        }

        protected Optional<String> getAuthorizationToken(IQueryInterface v, boolean expected) throws IOException {
            Optional<String> token = getAuthorizationToken(v);
            if ((!token.isPresent() || token.get().equals("")) && expected) {
                produceResponse(v, Optional.of("Authorization token is expected."), HTTPBADREQUEST);
                return Optional.empty();
            }
            return token;
        }

        protected String[] getPath(HttpExchange t) {
            String u = t.getRequestURI().getPath();
            String[] res = u.substring(1).split("/");
            return res;
        }

        private Optional<IQueryInterface> returnBad(IQueryInterface v, String errmess) throws IOException {
            RestLogger.L.severe(errmess);
            produceResponse(v, Optional.of(errmess), HTTPBADREQUEST);
            return Optional.empty();
        }

        private Optional<IQueryInterface> verifyURL(HttpExchange t, RestParams pars) throws IOException {

            final Map<String, RestParams.RestParam> params = pars.getParams();
            QueryInterface v = new QueryInterface(pars, t);

            RestLogger.debug(t.getRequestMethod() + " " + t.getRequestURI().getQuery());
            if (!verifyMethod(v)) return Optional.empty();
            if (tokenexpected && !getAuthorizationToken(v, tokenexpected).isPresent()) Optional.empty();
            // verify param
            // check if parameters allowed
            if (t.getRequestURI().getQuery() != null) {
                String qq = t.getRequestURI().getQuery();
                String query = URLDecoder.decode(qq, StandardCharsets.UTF_8.toString());
                String[] q = query.split("&");
                for (String qline : q) {
                    String[] vv = qline.split("=");
                    String s = vv[0];
                    String val = vv.length == 1 ? "" : vv[1];
                    if (!params.containsKey(s)) {
                        returnBad(v, "Parameter " + s + " not expected.");
                    }
                    // get value
                    RestParams.RestParam rpara = params.get(s);
                    switch (rpara.ptype) {
                        case BOOLEAN: {
                            if (val.equals("true") || val.equals("false")) {
                                v.values.put(s, new ParamValue(val.equals("true")));
                                break;
                            }
                            // incorrect true or false
                            String errmess = "Parameter " + s + " ? " + val + " true or false expected";
                            returnBad(v, errmess);
                        }
                        case INT: {
                            try {
                                int i = Integer.parseInt(val);
                                v.values.put(s, new ParamValue(i));
                                break;
                            } catch (NumberFormatException e) {
                                returnBad(v, "Parameter " + s + "?" + val + " incorrect integer value");
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
                        produceParameterNotFound(v, s);
                        return Optional.empty();
                    }
                    // set default value
                    v.values.put(s, params.get(s).defa);
                }
            }

            if (OPTIONS.equals(t.getRequestMethod())) {
                RestLogger.L.info(OPTIONS + " request");
                produceOKResponse(v, Optional.of("OK"));
                // return false, to avoid sending the content for OPTIONS
                return Optional.empty();
            }
            return Optional.of(v);
        }

        protected boolean getLogParam(IQueryInterface v, String param) {
            return v.getValues().get(param).logvalue;
        }

        protected int getIntParam(IQueryInterface v, String param) {
            return v.getValues().get(param).intvalue;
        }

        protected String getStringParam(IQueryInterface v, String param) {
            return v.getValues().get(param).stringvalue;
        }

        protected Optional<String> getStringParamExpected(IQueryInterface v, String param) throws IOException {
            String val = getStringParam(v, param);
            if (val == null || val.equals("")) {
                produceParameterNotFound(v, param);
                return Optional.empty();
            }
            return Optional.of(val);
        }
    }

    public static void registerService(HttpServer server, RestServiceHelper service) {
        RestLogger.info("Register service: " + service.url);
        server.createContext("/" + service.url, service);
    }

}
