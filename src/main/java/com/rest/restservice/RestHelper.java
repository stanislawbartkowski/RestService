package com.rest.restservice;

/*
 * Copyright 2024 stanislawbartkowski@gmail.com
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


import com.sun.net.httpserver.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.sql.Date;

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

    private static final String BOUNDARY = "974767299852498929531610575";
    private static final String BOUNDARYSEP = "--" + BOUNDARY;
    private static final String BOUNDARYCLOSING = BOUNDARYSEP + "--";


    private static Authenticator auth = null;

    public static void setAuth(Authenticator auth) {
        RestHelper.auth = auth;
    }

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

        /**
         * @return Request data if expected and exists
         */
        ByteBuffer getRequestData();
    }

    private static class QueryInterface implements IQueryInterface {

        private final Map<String, ParamValue> values = new HashMap<String, ParamValue>();
        private final RestParams pars;
        private final HttpExchange t;
        private final ByteBuffer data;

        private QueryInterface(RestParams pars, HttpExchange t, ByteBuffer data) {
            this.pars = pars;
            this.t = t;
            this.data = data;
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

        @Override
        public ByteBuffer getRequestData() {
            return data;
        }

    }

    /**
     * Helper class for handling REST service. The client service class should extend this abstract class.
     */
    abstract public static class RestServiceHelper implements HttpHandler {
        private final String url;

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
        public abstract void servicehandle(IQueryInterface v) throws IOException, InterruptedException;


        /**
         * Abstract method enforced by com.sun.net.httpserver.HttpHandler abstract class.https://stackoverflow.blog/2020/04/29/more-than-qa-how-the-stack-overflow-team-uses-stack-overflow-for-teams/?cb=1
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
                        return new RestParams(GET, Optional.empty(), false, new ArrayList<String>());
                    }

                    @Override
                    public HttpExchange getT() {
                        return httpExchange;
                    }

                    @Override
                    public ByteBuffer getRequestData() {
                        return null;
                    }
                }, Optional.of(e.getMessage()), HTTPBADREQUEST);
            }
            httpExchange.close();
        }

        /**
         * Constructor
         *
         * @param url The REST service URL, Important : without leading / (look registerService method)
         */
        protected RestServiceHelper(String url) {
            this.url = url;
        }

        private void addCORSHeader(IQueryInterface v) {
            HttpExchange t = v.getT();
            RestParams pars = v.getRestParams();
            StringBuilder bui = new StringBuilder(OPTIONS);
            for (String m : pars.getMethodsAllowed()) bui.append(", " + m);
            t.getResponseHeaders().set("Access-Control-Allow-Methods", bui.toString());
            t.getResponseHeaders().set("Allow:", bui.toString());
            String headersAllowed = "Access-Control-Allow-Headers, Origin, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization";
            if (pars.getHeadersAllowed().isPresent())
                headersAllowed = headersAllowed + "," + pars.getHeadersAllowed().get();
            // RestLogger.L.info("Headers allowed: " + headersAllowed);
            t.getResponseHeaders().set("Access-Control-Allow-Headers", headersAllowed);
            if (pars.isCrossedAllowed()) {
                t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            }
            if (pars.getResponseContent().isPresent()) {
                switch (pars.getResponseContent().get()) {
                    case JSON:
                        t.getResponseHeaders().set("Content-Type", "application/json");
                        break;
                    case TEXT:
                        t.getResponseHeaders().set("Content-Type", "text/plain");
                        break;
                    case ZIP:
                        t.getResponseHeaders().set("Content-Type", "application/zip");
                        break;
                    case JS:
                        t.getResponseHeaders().set("Content-Type", "text/javascript");
                        break;
                    case XML:
                        t.getResponseHeaders().set("Content-Type", "application/xml");
                        break;
                    case MIXED:
                        t.getResponseHeaders().set("Content-Type", "multipart/mixed;boundary=" + BOUNDARY);
                        break;
                }
            }
            t.getResponseHeaders().set("charset", "utf-8");
        }

        private final static int READERCHUNK = 1000;

        private void produceResponseFromInput(IQueryInterface v, Optional<InputStream> in, int HTTPResponse, Optional<String> token) throws IOException {
            addCORSHeader(v);
            HttpExchange t = v.getT();
            if (in.isEmpty()) {
                t.sendResponseHeaders(HTTPNODATA, 0);
                return;
            }
            byte[] buffer = new byte[READERCHUNK];
            int bytesread = 0;
            int bytessum = 0;
            // chunked transfer
            t.sendResponseHeaders(HTTPResponse, 0);
            try (OutputStream os = t.getResponseBody()) {
                while ((bytesread = in.get().read(buffer, 0, READERCHUNK)) != -1) {
                    bytessum += bytesread;
                    os.write(buffer,0, bytesread);
                }
            }
            if (bytessum == 0) {
                t.sendResponseHeaders(HTTPNODATA, 0);
            }
        }

        protected void produceResponseFromFile(IQueryInterface v, File in, boolean removefile, int HTTPResponse, Optional<String> token) throws IOException {
            try (FileInputStream fin = new FileInputStream(in)) {
                produceResponseFromInput(v, Optional.of(fin), HTTPResponse, token);
            }
            if (removefile) in.delete();
        }


        /**
         * General helper method to use by custom servicehandle method
         *
         * @param v            Context handler
         * @param response     Optional, response content as sequence of bytes, if empty the no content is returned.
         * @param HTTPResponse HTTP response code, some codes are specified in as public static attributes
         * @param token        Optional, security token to be included in the response
         * @throws IOException
         */
        protected void produceByteResponse(IQueryInterface v, Optional<byte[]> response, int HTTPResponse, Optional<String> token) throws IOException {
            if (response.isEmpty()) produceResponseFromInput(v, Optional.empty(), HTTPResponse, token);
            else {
                InputStream is = new ByteArrayInputStream(response.get());
                produceResponseFromInput(v, Optional.of(is), HTTPResponse, token);
            }
        }


        /**
         * General helper method to use by custom servicehandle method
         * The same as produceByteResponse, but the response is a string
         *
         * @param v            Context handler
         * @param message      Optional, response content, if empty the no content is returned.
         * @param HTTPResponse HTTP response code, some codes are specified in as public static attributes
         * @param token        Optional, security token to be included in the response
         * @throws IOException
         */

        protected void produceResponse(IQueryInterface v, Optional<String> message, int HTTPResponse, Optional<String> token) throws IOException {
            if (message.isPresent()) {
                Optional<byte[]> resp = Optional.of(message.get().getBytes());
                produceByteResponse(v, resp, HTTPResponse, token);
            } else produceByteResponse(v, Optional.empty(), HTTPResponse, token);
        }


        private byte[] concatenateBytes(byte a[], byte b[]) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(a);
            outputStream.write(b);
            return outputStream.toByteArray();
        }

        private byte[] concatenateBytes3(byte a[], byte b[], byte c[]) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(a);
            outputStream.write(b);
            outputStream.write(c);
            return outputStream.toByteArray();
        }

        private byte[] producePartResponse(String contenttype, Optional<String> m, Optional<byte[]> b, boolean addclose) throws IOException {
            String header = BOUNDARYSEP + System.lineSeparator() + "Content-Type:" + contenttype + System.lineSeparator();
            if (m.isPresent())
                return concatenateBytes(header.getBytes(), (m.get() + System.lineSeparator() + (addclose ? BOUNDARYCLOSING: "")).getBytes());
            if (b.isPresent()) {
                if (addclose) return concatenateBytes3(header.getBytes(), b.get(), BOUNDARYCLOSING.getBytes());
                return concatenateBytes(header.getBytes(), b.get());
            }
            return header.getBytes();
        }

        protected void produce2PartResponse(IQueryInterface v, Optional<String> message1, Optional<String> message2, int HTTPResponse, Optional<String> token) throws IOException {
            byte[] response = concatenateBytes(producePartResponse("application/json", message1, Optional.empty(), false),
                    producePartResponse("text/html", message2, Optional.empty(), true));
            Optional<byte[]> resp = Optional.of(response);
            produceByteResponse(v, resp, HTTPResponse, token);
        }

        protected void produce2PartByteResponse(IQueryInterface v, Optional<String> message1, Optional<byte[]> message2, int HTTPResponse, Optional<String> token) throws IOException {
            byte[] response = concatenateBytes(producePartResponse("application/json", message1, Optional.empty(), false),
                    producePartResponse("application/octet-stream", Optional.empty(), message2, true));
            Optional<byte[]> resp = Optional.of(response);
            produceByteResponse(v, resp, HTTPResponse, token);
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
            String errmess = "Parameter " + s + " not found in url";
            RestLogger.L.severe(errmess);
            produceResponse(v, Optional.of(errmess), HTTPBADREQUEST);
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
            String message = String.format("%s method expected, %s is used.", pars.getRequestMethod(), t.getRequestMethod());
            RestLogger.L.severe(message);
            produceResponse(v, Optional.of(message), HTTPMETHODNOTALLOWED);
            return false;
        }


        private final static int BUFCHUNK = 1000;

        private ByteBuffer getRequestData(HttpExchange t) throws IOException {
            InputStream i = t.getRequestBody();
            ByteBuffer b = ByteBuffer.allocate(0);
            while (true) {
                byte[] by = new byte[BUFCHUNK];
                int bread = i.read(by);
                if (bread == -1) break;
                if (bread == 0) continue;
                ByteBuffer newb = ByteBuffer.allocate(b.capacity() + bread);
                b.rewind();
                newb.put(b);
                newb.put(by, 0, bread);
                b = newb;
            }
            return b;
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
            ByteBuffer b = null;
            if (pars.isRequestDataExpected()) b = getRequestData(t);
            QueryInterface v = new QueryInterface(pars, t, b);

            RestLogger.debug(t.getRequestMethod() + " " + t.getRequestURI().getQuery());
            if (OPTIONS.equals(t.getRequestMethod())) {
                RestLogger.L.info(OPTIONS + " request");
                produceOKResponse(v, Optional.of("OK"));
                // return false, to avoid sending the content for OPTIONS
                return Optional.empty();
            }
            if (!verifyMethod(v)) return Optional.empty();
            if (pars.isRequestDataExpected() && b.capacity() == 0)
                return returnBad(v, "Request data expected but not found any");

            // verify param
            // check if parameters allowed
            if (t.getRequestURI().getQuery() != null) {
                String qq = t.getRequestURI().getQuery();
                String query = URLDecoder.decode(qq, StandardCharsets.UTF_8.toString());
                String[] q = query.split("&");
                for (String qline : q) {
                    int ipos = qline.indexOf('=');
                    String val = "";
                    String s = qline;
                    if (ipos != -1) {
                        s = qline.substring(0, ipos);
                        val = qline.substring(ipos + 1);
                    }
                    if (!params.containsKey(s)) {
                        return returnBad(v, "Parameter " + s + " not expected.");
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
                            return returnBad(v, errmess);
                        }
                        case DOUBLE: {
                            try {
                                double dd = Double.parseDouble(val);
                                v.values.put(s, new ParamValue(dd));
                                break;
                            } catch (NumberFormatException e) {
                                return returnBad(v, "Parameter " + s + "?" + val + " incorrect double value");
                            }
                        }
                        case STRING: {
                            v.values.put(s, new ParamValue(val));
                            break;
                        }
                        case DATE: {
                            SimpleDateFormat sqldateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            try {
                                Date da = new Date(sqldateFormat.parse(val).getTime());
                                v.values.put(s, new ParamValue(da));
                            } catch (ParseException p) {
                                return returnBad(v, "Parameter " + s + "?" + val + " incorrect date format, expected yyyy-MM-dd");
                            }
                            break;
                        }
                        case INT: {
                            try {
                                int ii = Integer.parseInt(val);
                                v.values.put(s, new ParamValue(ii));
                                break;
                            } catch (NumberFormatException e) {
                                return returnBad(v, "Parameter " + s + "?" + val + " incorrect int value");
                            }

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
         * Returns double value for query parameters.
         *
         * @param v     Context
         * @param param Query param name/key
         * @return double value
         */
        protected double getDoubleParam(IQueryInterface v, String param) {
            return v.getValues().get(param).getDoublevalue();
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
         * Returns date value for query parameters.
         *
         * @param v     Context
         * @param param Query param name/key
         * @return Date value
         */
        protected Date getDateParam(IQueryInterface v, String param) {
            return v.getValues().get(param).getDatevalue();
        }

        /**
         * Returns int value for query parameters.
         *
         * @param v     Context
         * @param param Query param name/key
         * @return int value
         */
        protected int getIntParam(IQueryInterface v, String param) {
            return v.getValues().get(param).getIntvalue();
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
        RestLogger.info("Register service: " + (service.url.equals("") ? "{root}" : service.url));
        HttpContext hc = server.createContext("/" + service.url, service);
        if (auth != null) hc.setAuthenticator(auth);

    }

}
