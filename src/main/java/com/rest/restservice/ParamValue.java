package com.rest.restservice;

/*
 * Copyright 2020 stanislawbartkowski@gmail.com
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


/**
 * Parameter values picked up from the query string. It is the responsibility of the application to request the propoer value.
 * The constructors reflects PARAMTYPE enum and specifies expected parameter value type.
 */

public class ParamValue {

    final boolean logvalue;
    final int intvalue;
    final String stringvalue;

    /** The parameter was BOOLEAN */
    public boolean isLogTrue() {
        return logvalue;
    }

    /** The parameter was integer number, INT */
    public int getIntvalue() {
        return intvalue;
    }

    /** The parameter was string (ny other) value, STRING */
    public String getStringvalue() {
        return stringvalue;
    }

    /** Default contructor (generic requirement) */
    ParamValue() {
        this.logvalue = false;
        this.intvalue = -1;
        this.stringvalue = null;
    }

    /** BOOLEAN parameter */
    public ParamValue(boolean logvalue) {
        this.logvalue = logvalue;
        this.intvalue = -1;
        this.stringvalue = null;
    }

    /** INT parameter */
    public ParamValue(int intvalue) {
        this.intvalue = intvalue;
        this.logvalue = false;
        this.stringvalue = null;
    }

    /** STRING parameter */
    public ParamValue(String stringvalue) {
        this.intvalue = -1;
        this.logvalue = false;
        this.stringvalue = stringvalue;
    }
}
