package com.rest.restservice;

import com.rest.restservice.ssl.SecureHttp;
import com.sun.net.httpserver.HttpsServer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class SecureHttpTest {

    private static final String PASSWORD = "changeit";

    private Path keystorePath;
    private HttpsServer server;
    private int port;
    private SSLContext clientCtx;

    @BeforeClass
    public void startServer() throws Exception {
        keystorePath = Files.createTempFile("rest-ssl-", ".jks");
        Files.delete(keystorePath);

        String javaHome = System.getProperty("java.home");
        String keytool = javaHome + "/bin/keytool"
                + (System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "");

        ProcessBuilder pb = new ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", "test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-dname", "CN=localhost",
                "-keystore", keystorePath.toString(),
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-storetype", "JKS"
        ).redirectErrorStream(true);

        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new SkipException("keytool not available: " + e.getMessage());
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new RuntimeException("keytool timed out");
        }
        if (p.exitValue() != 0) {
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException("keytool failed: " + out);
        }

        server = SecureHttp.produceHttps(0, new String[]{keystorePath.toString(), PASSWORD});
        port = server.getAddress().getPort();
        RestHelper.registerService(server, new RestHelperTest.IntEchoService());
        server.start();

        clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {
            }

            public void checkServerTrusted(X509Certificate[] c, String a) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() throws IOException {
        if (server != null) server.stop(0);
        if (keystorePath != null) Files.deleteIfExists(keystorePath);
    }

    @Test
    public void httpsGet_returnsExpectedResponse() throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL("https://127.0.0.1:" + port + "/int?n=42").openConnection();
        c.setSSLSocketFactory(clientCtx.getSocketFactory());
        c.setHostnameVerifier((h, s) -> true);
        c.setRequestMethod("GET");
        assertEquals(c.getResponseCode(), 200);
        try (InputStream is = c.getInputStream()) {
            assertEquals(RestHelper.toS(is), "int=42");
        }
    }

    @Test
    public void produceHttps_missingKeystoreFile_throwsIOException() {
        try {
            SecureHttp.produceHttps(0, new String[]{"/nonexistent/keystore.jks", "x"});
            fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    @Test
    public void sslParam_readsKeystoreAndPasswordFromPropertiesFile() throws IOException {
        Path props = Files.createTempFile("ssl-", ".properties");
        Files.writeString(props,
                "store.key.filename=/some/keystore.jks\n"
                        + "key.store.password=secret\n");
        try {
            String[] parts = SSLParam.readConf(props.toString());
            assertEquals(parts.length, 2);
            assertEquals(parts[0], "/some/keystore.jks");
            assertEquals(parts[1], "secret");
        } finally {
            Files.deleteIfExists(props);
        }
    }

    @Test
    public void sslParam_missingPasswordKey_throwsIOException() throws IOException {
        Path props = Files.createTempFile("ssl-", ".properties");
        Files.writeString(props, "store.key.filename=/some/keystore.jks\n");
        try {
            SSLParam.readConf(props.toString());
            fail("expected IOException for missing password key");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("key.store.password"), e.getMessage());
        } finally {
            Files.deleteIfExists(props);
        }
    }
}