package com.rest.restservice;

import com.sun.corba.se.impl.corba.CORBAObjectImpl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

public class RestParams {

    private final Map<String, RestParam> params = new HashMap<String, RestParam>();
    private final String requestMethod;
    private final Optional<CONTENT> responseContent;

    public List<String> getMethodsAllowed() {
        return methodsAllowed;
    }

    private final List<String> methodsAllowed;

    public boolean isCrossedAllowed() {
        return crossedAllowed;
    }

    private final boolean crossedAllowed;


    public RestParams(String requestMethod, Optional<CONTENT> responseContent, boolean crossedAllowed, List<String> methodsAllowed) {
        this.requestMethod = requestMethod;
        this.responseContent = responseContent;
        this.crossedAllowed = crossedAllowed;
        this.methodsAllowed = methodsAllowed;
    }

    public Map<String, RestParam> getParams() {
        return params;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public Optional<CONTENT> getResponseCpntent() {
        return responseContent;
    }

    public enum CONTENT {
        TEXT, JSON
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

    public void addParam(String paramName, PARAMTYPE ptype) {
        params.put(paramName, new RestParam(ptype));
    }

    public void addParam(String paramName, PARAMTYPE ptype, ParamValue defa) {
        params.put(paramName, new RestParam(ptype, defa));
    }

}
