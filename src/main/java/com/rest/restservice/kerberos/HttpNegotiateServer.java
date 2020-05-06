package com.rest.restservice.kerberos;

/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6578647 6829283
 * @run main/othervm HttpNegotiateServer
 * @summary Undefined requesting URL in java.net.Authenticator.getPasswordAuthentication()
 * @summary HTTP/Negotiate: Authenticator triggered again when user cancels the first one
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
import sun.security.jgss.GSSUtil;

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

                    cred = m.createCredential(null,
                            GSSCredential.DEFAULT_LIFETIME,
                            GSSUtil.GSS_SPNEGO_MECH_OID,
                            GSSCredential.ACCEPT_ONLY);
                    cred.add(cred.getName(), GSSCredential.INDEFINITE_LIFETIME, GSSCredential.INDEFINITE_LIFETIME, GSSUtil.GSS_KRB5_MECH_OID, GSSCredential.ACCEPT_ONLY);

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

