package com.rest.restservice;

import java.util.Map;
import java.util.HashMap;

public class RestParams {

    public Map<String, RestParam> getParams() {
        return params;
    }

    private final Map<String, RestParam> params = new HashMap<String, RestParam>();

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

    public  void addParam(String paramName, PARAMTYPE ptype) {
        params.put(paramName, new RestParam(ptype));
    }

    public void addParam(String paramName, PARAMTYPE ptype, ParamValue defa) {
        params.put(paramName, new RestParam(ptype, defa));
    }

}
