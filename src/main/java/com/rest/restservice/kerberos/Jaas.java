package com.rest.restservice.kerberos;

/*
 * Copyright 2022 stanislawbartkowski@gmail.com
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

import javax.security.auth.Subject;
import javax.security.auth.login.*;
import javax.security.auth.callback.CallbackHandler;
import java.security.*;

import com.rest.restservice.RestLogger;
import com.sun.security.auth.callback.TextCallbackHandler;

class Jaas {

    static void loginAndAction(String name, PrivilegedExceptionAction action)
            throws LoginException, PrivilegedActionException {

        // Create a callback handler
        CallbackHandler callbackHandler = new TextCallbackHandler();

        LoginContext context = null;

        // Create a LoginContext with a callback handler
        context = new LoginContext(name, callbackHandler);

        // Perform authentication
        context.login();

        // Perform action as authenticated user
        Subject subject = context.getSubject();
        RestLogger.info("Authenticated principal: " + subject.getPrincipals());

        Subject.doAs(subject, action);

        context.logout();
    }

}