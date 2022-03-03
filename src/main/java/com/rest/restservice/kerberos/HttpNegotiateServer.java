package com.rest.restservice.kerberos;

/*
 * Copyright 2021 stanislawbartkowski@gmail.com
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

import com.rest.restservice.RestLogger;
import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
//import sun.security.jgss.GSSUtil;
import com.sun.security.jgss.GSSUtil;


import java.net.HttpURLConnection;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import java.util.logging.Level;

/**
 * Basic JGSS/krb5 test with 3 parties: client, server, backend server. Each
 * party uses JAAS login to get subjects and executes JGSS calls using
 * Subject.doAs.
 */
public class HttpNegotiateServer {

    public static Authenticator constructNegotiateAuthenticator() throws Exception {
        return new MyServerAuthenticator(false, NEGOTIATE);
    }

    private static final String NEGOTIATE = "Negotiate";

    private static class MyServerAuthenticator
            extends Authenticator {
        private GSSManager m;
        private GSSCredential cred;
        private final String scheme;
        private final String reqHdr;
        private final String respHdr;
        private final int err;

        MyServerAuthenticator(boolean proxy, String scheme) throws Exception {

            this.scheme = scheme;
            reqHdr = proxy ? "Proxy-Authenticate" : "WWW-Authenticate";
            respHdr = proxy ? "Proxy-Authorization" : "Authorization";
            err = proxy ? HttpURLConnection.HTTP_PROXY_AUTH : HttpURLConnection.HTTP_UNAUTHORIZED;

            Jaas.loginAndAction("server", new PrivilegedExceptionAction() {

                @Override
                public Object run() throws Exception {
                    RestLogger.info("Creating GSSCredential");
                    m = GSSManager.getInstance();
                    Oid spnegoOid = new Oid("1.2.840.113554.1.2.2");

                    Oid GSS_KRB5_MECH_OID = new Oid("1.2.840.113554.1.2.2");

//                    Oid GSS_KRB5_MECH_OID = GSSUtil.GSS_KRB5_MECH_OID


                    cred = m.createCredential(null,
                            GSSCredential.DEFAULT_LIFETIME,
//                            GSSUtil.GSS_SPNEGO_MECH_OID,
                            spnegoOid,
                            GSSCredential.ACCEPT_ONLY);
                    cred.add(cred.getName(), GSSCredential.INDEFINITE_LIFETIME, GSSCredential.INDEFINITE_LIFETIME, GSS_KRB5_MECH_OID, GSSCredential.ACCEPT_ONLY);

                    return cred;
                }
            });
        }

        @Override
        public Result authenticate(HttpExchange exch) {
            // The GSContext is stored in an HttpContext attribute named
            // "GSSContext" and is created at the first request.
            String auth = exch.getRequestHeaders().getFirst(respHdr);
            try {
                GSSContext c = null;
                if (auth == null) {                 // First request
                    Headers map = exch.getResponseHeaders();
                    map.add(reqHdr, scheme);        // Challenge!
                    GSSContext conte = m.createContext(cred);
                    exch.getHttpContext().getAttributes().put("GSSContext", conte);
                    return new Authenticator.Retry(err);
                } else {                            // Later requests
                    c = (GSSContext) exch.getHttpContext().getAttributes().get("GSSContext");
                    byte[] token = Base64.getMimeDecoder().decode(auth.split(" ")[1]);

                    token = c.acceptSecContext(token, 0, token.length);
                    RestLogger.info("Authenticated as: " + c.getSrcName());

                    Headers map = exch.getResponseHeaders();
                    map.set(reqHdr, scheme + " " + Base64.getMimeEncoder()
                            .encodeToString(token).replaceAll("\\s", ""));
                    if (c.isEstablished()) {
                        return new Authenticator.Success(
                                new HttpPrincipal(c.getSrcName().toString(), ""));
                    } else {
                        return new Authenticator.Retry(err);
                    }
                }
            } catch (Exception e) {
                RestLogger.L.log(Level.SEVERE, "Cannot authenticate ticket", e);
                return new Authenticator.Failure(HttpURLConnection.HTTP_INTERNAL_ERROR);
            }
        }
    }
}

