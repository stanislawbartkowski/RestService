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


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.lang.management.OperatingSystemMXBean;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Main helper module. Contains supporting logic for handling REST services. The custom service class should extend RestServiceHelper class and implement two abstract methods.<br>
 * General overwiew: <br>
 * IQeuryInterface - request service context, returns HTTPExchange, parsed query values and query parameters definition.<br>
 * RestServiceHelper - the custom class should extend this abstract class<br>
 */

public class RestHelper {

    /**
     * public values, HTTP methods
     */
    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String OPTIONS = "OPTIONS";

    private static final String TOKEN = "Token";
    private static final String AUTHORIZATION = "Authorization";

    /**
     * public values, used HTTP code responses
     */
    public static final int HTTPOK = HttpURLConnection.HTTP_OK;
    public static final int HTTPNODATA = HttpURLConnection.HTTP_NO_CONTENT;
    public static final int HTTPMETHODNOTALLOWED = HttpURLConnection.HTTP_BAD_METHOD;
    public static final int HTTPBADREQUEST = HttpURLConnection.HTTP_BAD_REQUEST;

    /**
     * Helper method to read InputStream to String
     *
     * @param i InputStream
     * @return String
     * @throws IOException in case of error
     */
    public static String toS(InputStream i) throws IOException {

        final int bufferSize = 1024;
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(i, StandardCharsets.UTF_8);
        int charsRead;
        while ((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
            out.append(buffer, 0, charsRead);
        }
        return out.toString();
    }


    /**
     * Context, QueryInterface used to interact between handle method and RestHelper services
     */
    public interface IQueryInterface {
        /**
         * THe parsed query URL part pairing query parameter and current value.
         * Important: it is the responsibility of the application to get proper (boolean, int or string) value from the map
         *
         * @return Map query parameter to value retrieved from URL
         */
        Map<String, ParamValue> getValues();

        /**
         * REST service definition
         *
         * @return RestParams class containing the REST service specification.
         */
        RestParams getRestParams();

        /**
         * @return Current HTTPExchange. It is the same value as parameter to getParams abstract method.
         */
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

    /**
     * Helper class for handling REST service. The client service class should extend this abstract class.
     */
    abstract public static class RestServiceHelper implements HttpHandler {
        private final String url;
        private final boolean tokenexpected;

        /**
         * Abstract method to be implemented. Is called only once after the REST query was received and is valid during current call.
         * The method can dynamically define REST service according to REST url.
         *
         * @param httpExchange Current HTTPExchange
         * @return RestParam current REST call specification.
         * @throws IOException In case of any problem. It the exception is throws then the servicehandle method is not called.
         */
        public abstract RestParams getParams(HttpExchange httpExchange) throws IOException;

        /**
         * Custom logic to handle REST request
         *
         * @param v Context,
         *          getValues contains parsed URL query parameter values
         *          getRestParans : returned by getParams
         *          getT : current HTTPExchange handler
         * @throws IOException
         */
        public abstract void servicehandle(IQueryInterface v) throws IOException;


        /**
         * Abstract method enforced by com.sun.net.httpserver.HttpHandler abstract class.
         *
         * @param httpExchange HttpExchange
         * @throws IOException
         */
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            // main logic of REST service handling
            try {
                // call custom (abstract) method to get REST service specification
                RestParams prest = getParams(httpExchange);
                // reads and validates query parameters, if any error found (for instance: incorrect query parameter value), return proper HTTP error code
                Optional<IQueryInterface> v = verifyURL(httpExchange, prest);
                // if any error found (for instance: incorrect query parameter value), return proper HTTP error code
                if (!v.isPresent()) return;
                // call abstract method, custom REST service logic
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
                        return new RestParams(GET, Optional.empty(), false, new ArrayList<String>(), Optional.empty());
                    }

                    @Override
                    public HttpExchange getT() {
                        return httpExchange;
                    }
                }, Optional.of(e.getMessage()), HTTPBADREQUEST);
            }
        }

        /**
         * Constructor
         *
         * @param url           The REST service URL, Important : without leading / (look registerService method)
         * @param tokenexpected If security token is expected for this call
         */
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
                String headersAllowed = "Access-Control-Allow-Headers, Origin, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers";
                if (pars.getHeadersAllowed().isPresent())
                    headersAllowed = headersAllowed + "," + pars.getHeadersAllowed().get();
                t.getResponseHeaders().set("Access-Control-Allow-Headers", headersAllowed);
            }
            if (pars.getResponseContent().isPresent()) {
                switch (pars.getResponseContent().get()) {
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

        private void addTokenHeader(IQueryInterface v, String token) {
            v.getT().getResponseHeaders().set(AUTHORIZATION, TOKEN + " " + token);
        }

        /**
         * General helper method to use by custom servicehandle method
         *
         * @param v            Context handler
         * @param message      Optionnal, response content, if empty the no content is returned.
         * @param HTTPResponse HTTP response code, some codes are specified in as public static attributes
         * @param token        Optional, security token to be included in the response
         * @throws IOException
         */
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

        /**
         * Overloaded produceResponse, empty security token
         */
        protected void produceResponse(IQueryInterface v, Optional<String> message, int HTTPResponse) throws IOException {
            produceResponse(v, message, HTTPResponse, Optional.empty());
        }

        /**
         * Overloaded produceResponse, should be used if the expected query URL parameter is not found
         *
         * @param v Context
         * @param s query parameter name
         * @throws IOException
         */
        protected void produceParameterNotFound(IQueryInterface v, String s) throws IOException {
            produceResponse(v, Optional.of("Parameter " + s + " not found in url"), HTTPBADREQUEST);
        }


        /**
         * Overloaded produceResponse, HTTPOK response code is included
         */
        protected void produceOKResponse(IQueryInterface v, Optional<String> message, Optional<String> token) throws IOException {
            produceResponse(v, message, HTTPOK, token);
        }

        /**
         * Overloaded prodecureResponse, HTTPOK response code and empty token
         */
        protected void produceOKResponse(IQueryInterface v, Optional<String> message) throws IOException {
            produceOKResponse(v, message, Optional.empty());
        }

        /**
         * Overloaded produceResponse, if message null or empty include HTTPNODATA response code
         */
        protected void produceOKResponse(IQueryInterface v, String message) throws IOException {
            if (message == null || message.equals("")) produceNODATAResponse(v);
            else produceOKResponse(v, Optional.of(message), Optional.empty());
        }

        /**
         * Overloaded produceResponse, returns HTTPNODATA response code
         */
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

        /**
         * Helper method, extract authorizarion token if expected
         *
         * @param v Context
         * @return Empty if authorization token not found in the HTTP header
         */
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

        /**
         * Get authorization token, if token expected prepare HTTPBADREUQUEST response if token not found
         *
         * @param v        Context
         * @param expected if true and token not found produce HTTPBADREQUEST response
         * @return if Optional.empty do not proceed, HTTPBADREQUEST response already sent
         * @throws IOException
         */
        protected Optional<String> getAuthorizationToken(IQueryInterface v, boolean expected) throws IOException {
            Optional<String> token = getAuthorizationToken(v);
            if ((!token.isPresent() || token.get().equals("")) && expected) {
                produceResponse(v, Optional.of("Authorization token is expected."), HTTPBADREQUEST);
                return Optional.empty();
            }
            return token;
        }

        /**
         * Parse URL path without query parameters. Break path into subpath
         *
         * @param t Context
         * @return String[] containing URL path broken to subpaths.
         */
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

        /**
         * Returns logical value for query parameters.
         *
         * @param v     Context
         * @param param Query param name/key
         * @return logical value
         */
        protected boolean getLogParam(IQueryInterface v, String param) {
            return v.getValues().get(param).logvalue;
        }

        /**
         * Returns integer value for query parameters.
         *
         * @param v     Context
         * @param param Query param name/key
         * @return integer value
         */
        protected int getIntParam(IQueryInterface v, String param) {
            return v.getValues().get(param).intvalue;
        }

        /**
         * Returns string value for query parameters.
         *
         * @param v     Context
         * @param param Query param name/key
         * @return string value
         */
        protected String getStringParam(IQueryInterface v, String param) {
            return v.getValues().get(param).stringvalue;
        }

        /**
         * Gets string query value. Produces HTTPBADREQUEST response if parameter is not specified
         *
         * @param v     Context
         * @param param Key/param name
         * @return Optional. If empty error response is already produced and do not proceed.
         * @throws IOException
         */
        protected Optional<String> getStringParamExpected(IQueryInterface v, String param) throws IOException {
            String val = getStringParam(v, param);
            if (val == null || val.equals("")) {
                produceParameterNotFound(v, param);
                return Optional.empty();
            }
            return Optional.of(val);
        }

        /**
         * Get request body as string, allows deploying data
         *
         * @param v Context
         * @return String, request body
         * @throws IOException
         */
        protected String getRequestBodyString(IQueryInterface v) throws IOException {
            InputStream is = v.getT().getRequestBody();
            return toS(is);
        }
    }

    /**
     * Helper method, register service class in JDK com.sun.net.httpserver.HttpServer;
     *
     * @param server  com.sun.net.httpserver.HttpServer instance
     * @param service Service class
     */
    public static void registerService(HttpServer server, RestServiceHelper service) {
        RestLogger.info("Register service: " + service.url);
        server.createContext("/" + service.url, service);
    }

}
