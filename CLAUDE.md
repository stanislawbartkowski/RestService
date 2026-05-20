# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build the JAR
mvn clean package

# Install into local Maven repository (makes it available as a dependency for other local projects)
./mvndeploy.sh

# Deploy to GitHub Maven Packages (requires credentials in ~/.m2/settings.xml)
mvn deploy -Dmaven.test.skip=true -Dmaven.resolver.transport=wagon
```

There are no tests in this project.

## Architecture

This is a **Java library** (not a runnable application) that wraps the JDK's built-in `com.sun.net.httpserver.HttpServer` to provide a lightweight REST framework. It is packaged as `restservice-1.0.jar` and consumed as a Maven dependency by other projects.

### How consumers use this library

1. **Start the server** — call `RestStart.RestStart(port, singleThread, registerServices, sslParams)`. Pass an empty `sslParams` array for plain HTTP; pass keystore parameters for HTTPS.
2. **Define endpoints** — subclass `RestHelper.RestServiceHelper` for each endpoint. Implement:
   - `getParams(HttpExchange)` → return a `RestParams` describing the HTTP method, allowed CORS, response content type, and expected query parameters.
   - `servicehandle(IQueryInterface)` → handle the request; use the helper `produce*Response` methods to write the response.
3. **Register endpoints** — call `RestHelper.registerService(server, myServiceInstance)` inside the `registerServices` consumer lambda.

### Key classes

| Class | Role |
|---|---|
| `RestStart` | Static entry point; creates `HttpServer` or `HttpsServer` and wires Kerberos auth if JAAS config is present |
| `RestHelper` | Core framework: `RestServiceHelper` (abstract base for handlers), `IQueryInterface` (per-request context), URL parsing/validation, all `produce*Response` helpers |
| `RestParams` | Describes a single endpoint: HTTP method, CORS policy, response `CONTENT` type, map of expected `RestParam` query parameters |
| `PARAMTYPE` | Enum of supported query parameter types: `BOOLEAN`, `INT`, `DOUBLE`, `STRING`, `DATE` |
| `ParamValue` | Typed wrapper for a parsed query parameter value |
| `SSLParam` | Reads keystore path and password from a `.properties` file for HTTPS setup |
| `ssl/SecureHttp` | Creates and configures `HttpsServer` with a JKS keystore |
| `kerberos/HttpNegotiateServer` | Implements SPNEGO/Kerberos `Authenticator`; activated when `java.security.auth.login.config` system property is set |

### Request lifecycle

`HttpServer` calls `RestServiceHelper.handle()` → `getParams()` (implementor-defined) → `verifyURL()` (validates method, parses and type-checks all query params, reads request body if `requestDataExpected`) → `servicehandle()` (implementor-defined). Any exception in this chain returns `400 Bad Request`.

### Multipart responses

`RestParams.CONTENT.MIXED` sets `Content-Type: multipart/mixed`. Use `produce2PartResponse` (JSON + HTML text) or `produce2PartByteResponse` (JSON + binary) for two-part responses. The boundary string is a fixed constant inside `RestHelper`.

### Maven coordinates

```xml
<dependency>
    <groupId>com.restservice</groupId>
    <artifactId>restservice</artifactId>
    <version>1.0</version>
</dependency>
```
