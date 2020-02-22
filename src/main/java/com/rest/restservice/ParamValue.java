package com.rest.restservice;


public class ParamValue {
    final boolean logvalue;
    final int intvalue;
    final String stringvalue;

    public boolean isLogTrue() {
        return logvalue;
    }

    public int getIntvalue() {
        return intvalue;
    }

    public String getStringvalue() {
        return stringvalue;
    }

    ParamValue() {
        this.logvalue = false;
        this.intvalue = -1;
        this.stringvalue = null;
    }

    ParamValue(boolean logvalue) {
        this.logvalue = logvalue;
        this.intvalue = -1;
        this.stringvalue = null;
    }

    ParamValue(int intvalue) {
        this.intvalue = intvalue;
        this.logvalue = false;
        this.stringvalue = null;
    }

    public ParamValue(String stringvalue) {
        this.intvalue = -1;
        this.logvalue = false;
        this.stringvalue = stringvalue;
    }
}
