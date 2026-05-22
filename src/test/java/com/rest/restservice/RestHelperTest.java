package com.rest.restservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RestHelperTest {

    private HttpServer server;
    private int port;

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        RestHelper.registerService(server, new IntEchoService());
        RestHelper.registerService(server, new AllTypesService());
        RestHelper.registerService(server, new OptionalDefaultService());
        RestHelper.registerService(server, new RequiresBodyService());
        RestHelper.registerService(server, new PostEchoService());
        RestHelper.registerService(server, new MultipartService());
        RestHelper.registerService(server, new PathInspectService());

        server.start();
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    // ---------------- HTTP helpers ----------------

    private String url(String path) {
        return "http://127.0.0.1:" + port + "/" + path;
    }

    private static class Response {
        final int code;
        final String body;
        final Map<String, List<String>> headers;

        Response(int code, String body, Map<String, List<String>> headers) {
            this.code = code;
            this.body = body;
            this.headers = headers;
        }

        String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().isEmpty() ? null : e.getValue().get(0);
                }
            }
            return null;
        }
    }

    private Response request(String method, String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(path)).openConnection();
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String s = is == null ? "" : RestHelper.toS(is);
        return new Response(code, s, c.getHeaderFields());
    }

    private Response get(String path) throws IOException {
        return request("GET", path, null);
    }

    private Response options(String path) throws IOException {
        return request("OPTIONS", path, null);
    }

    // ---------------- toS ----------------

    @Test
    public void toS_readsUtf8FromStream() throws IOException {
        String s = "héllo, world ✓";
        ByteArrayInputStream is = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        assertEquals(RestHelper.toS(is), s);
    }

    @Test
    public void toS_emptyStreamProducesEmptyString() throws IOException {
        assertEquals(RestHelper.toS(new ByteArrayInputStream(new byte[0])), "");
    }

    // ---------------- INT param + method + CORS + OPTIONS ----------------

    static class IntEchoService extends RestHelper.RestServiceHelper {
        IntEchoService() {
            super("int");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            RestParams p = new RestParams(RestHelper.GET, Optional.of(RestParams.CONTENT.TEXT), true,
                    Arrays.asList(RestHelper.GET));
            p.addParam("n", PARAMTYPE.INT);
            return p;
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            produceOKResponse(v, "int=" + getIntParam(v, "n"));
        }
    }

    @Test
    public void intParam_parsedCorrectly() throws IOException {
        Response r = get("int?n=42");
        assertEquals(r.code, 200);
        assertEquals(r.body, "int=42");
        assertEquals(r.header("Content-Type"), "text/plain");
    }

    @Test
    public void intParam_badValue_returns400() throws IOException {
        Response r = get("int?n=notanint");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("incorrect int value"), r.body);
    }

    @Test
    public void intParam_missingObligatory_returns400() throws IOException {
        Response r = get("int");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("not found"), r.body);
    }

    @Test
    public void unknownParam_returns400() throws IOException {
        Response r = get("int?n=1&extra=foo");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("not expected"), r.body);
    }

    @Test
    public void wrongMethod_returns405() throws IOException {
        Response r = request("PUT", "int?n=1", "");
        assertEquals(r.code, 405);
    }

    @Test
    public void optionsRequest_returns200WithCorsHeaders() throws IOException {
        Response r = options("int");
        assertEquals(r.code, 200);
        assertEquals(r.body, "OK");
        assertEquals(r.header("Access-Control-Allow-Origin"), "*");
        String allowedMethods = r.header("Access-Control-Allow-Methods");
        assertTrue(allowedMethods.contains("GET"));
        assertTrue(allowedMethods.contains("OPTIONS"));
    }

    // ---------------- All PARAMTYPEs ----------------

    static class AllTypesService extends RestHelper.RestServiceHelper {
        AllTypesService() {
            super("types");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            RestParams p = new RestParams(RestHelper.GET, Optional.of(RestParams.CONTENT.JSON), true,
                    Arrays.asList(RestHelper.GET));
            p.addParam("b", PARAMTYPE.BOOLEAN);
            p.addParam("d", PARAMTYPE.DOUBLE);
            p.addParam("s", PARAMTYPE.STRING);
            p.addParam("dt", PARAMTYPE.DATE);
            return p;
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            String json = String.format("{\"b\":%s,\"d\":%s,\"s\":\"%s\",\"dt\":\"%s\"}",
                    getLogParam(v, "b"),
                    getDoubleParam(v, "d"),
                    getStringParam(v, "s"),
                    getDateParam(v, "dt"));
            produceOKResponse(v, json);
        }
    }

    @Test
    public void allTypes_parsedAndReturnedAsJson() throws IOException {
        Response r = get("types?b=true&d=3.14&s=hello&dt=2024-12-31");
        assertEquals(r.code, 200);
        assertEquals(r.header("Content-Type"), "application/json");
        assertTrue(r.body.contains("\"b\":true"), r.body);
        assertTrue(r.body.contains("\"d\":3.14"), r.body);
        assertTrue(r.body.contains("\"s\":\"hello\""), r.body);
        assertTrue(r.body.contains("\"dt\":\"2024-12-31\""), r.body);
    }

    @Test
    public void booleanParam_invalid_returns400() throws IOException {
        Response r = get("types?b=yes&d=1&s=x&dt=2024-01-01");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("true or false"), r.body);
    }

    @Test
    public void doubleParam_invalid_returns400() throws IOException {
        Response r = get("types?b=true&d=notadouble&s=x&dt=2024-01-01");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("incorrect double value"), r.body);
    }

    @Test
    public void dateParam_invalid_returns400() throws IOException {
        Response r = get("types?b=true&d=1&s=x&dt=not-a-date");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("incorrect date format"), r.body);
    }

    // ---------------- Optional parameter with default ----------------

    static class OptionalDefaultService extends RestHelper.RestServiceHelper {
        OptionalDefaultService() {
            super("optional");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            RestParams p = new RestParams(RestHelper.GET, Optional.of(RestParams.CONTENT.TEXT), true,
                    Arrays.asList(RestHelper.GET));
            p.addParam("n", PARAMTYPE.INT, new ParamValue(99));
            return p;
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            produceOKResponse(v, "n=" + getIntParam(v, "n"));
        }
    }

    @Test
    public void optionalParam_defaultUsedWhenMissing() throws IOException {
        Response r = get("optional");
        assertEquals(r.code, 200);
        assertEquals(r.body, "n=99");
    }

    @Test
    public void optionalParam_explicitValueOverridesDefault() throws IOException {
        Response r = get("optional?n=7");
        assertEquals(r.code, 200);
        assertEquals(r.body, "n=7");
    }

    // ---------------- requestDataExpected ----------------

    static class RequiresBodyService extends RestHelper.RestServiceHelper {
        RequiresBodyService() {
            super("body");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            return new RestParams(RestHelper.POST, Optional.of(RestParams.CONTENT.TEXT), true,
                    Arrays.asList(RestHelper.POST), Optional.empty(), true);
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            byte[] data = new byte[v.getRequestData().capacity()];
            v.getRequestData().rewind();
            v.getRequestData().get(data);
            produceOKResponse(v, "got:" + new String(data, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void postWithBody_isAvailableAsByteBuffer() throws IOException {
        Response r = request("POST", "body", "hello-body");
        assertEquals(r.code, 200);
        assertEquals(r.body, "got:hello-body");
    }

    @Test
    public void postWithoutExpectedBody_returns400() throws IOException {
        Response r = request("POST", "body", "");
        assertEquals(r.code, 400);
        assertTrue(r.body.contains("Request data expected"), r.body);
    }

    // ---------------- getRequestBodyString ----------------

    static class PostEchoService extends RestHelper.RestServiceHelper {
        PostEchoService() {
            super("echo");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            return new RestParams(RestHelper.POST, Optional.of(RestParams.CONTENT.TEXT), true,
                    Arrays.asList(RestHelper.POST));
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            produceOKResponse(v, "echo:" + getRequestBodyString(v));
        }
    }

    @Test
    public void getRequestBodyString_readsPostBody() throws IOException {
        Response r = request("POST", "echo", "payload");
        assertEquals(r.code, 200);
        assertEquals(r.body, "echo:payload");
    }

    // ---------------- Multipart response ----------------

    static class MultipartService extends RestHelper.RestServiceHelper {
        MultipartService() {
            super("mix");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            return new RestParams(RestHelper.GET, Optional.of(RestParams.CONTENT.MIXED), true,
                    Arrays.asList(RestHelper.GET));
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            produce2PartResponse(v, Optional.of("{\"a\":1}"), Optional.of("<html/>"),
                    RestHelper.HTTPOK, Optional.empty());
        }
    }

    @Test
    public void multipartResponse_containsBothPartsAndBoundary() throws IOException {
        Response r = get("mix");
        assertEquals(r.code, 200);
        String contentType = r.header("Content-Type");
        assertTrue(contentType != null && contentType.startsWith("multipart/mixed;boundary="), contentType);
        assertTrue(r.body.contains("application/json"), r.body);
        assertTrue(r.body.contains("{\"a\":1}"), r.body);
        assertTrue(r.body.contains("text/html"), r.body);
        assertTrue(r.body.contains("<html/>"), r.body);
        assertTrue(r.body.contains("--974767299852498929531610575"), r.body);
    }

    // ---------------- getPath ----------------

    static class PathInspectService extends RestHelper.RestServiceHelper {
        PathInspectService() {
            super("p");
        }

        @Override
        public RestParams getParams(HttpExchange t) {
            return new RestParams(RestHelper.GET, Optional.of(RestParams.CONTENT.TEXT), true,
                    Arrays.asList(RestHelper.GET));
        }

        @Override
        public void servicehandle(RestHelper.IQueryInterface v) throws IOException {
            produceOKResponse(v, String.join("|", getPath(v.getT())));
        }
    }

    @Test
    public void getPath_splitsPathByForwardSlash() throws IOException {
        Response r = get("p/a/b/c");
        assertEquals(r.code, 200);
        assertEquals(r.body, "p|a|b|c");
    }
}