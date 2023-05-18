package com.rest.restservice;

/*
 * Copyright 2023 stanislawbartkowski@gmail.com
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


import java.sql.Date;

/**
 * Parameter values picked up from the query string. It is the responsibility of the application to request the propoer value.
 * The constructors reflects PARAMTYPE enum and specifies expected parameter value type.
 */

public class ParamValue {

    final boolean logvalue;
    final double doublevalue;
    final String stringvalue;
    final Date datevalue;
    final int intvalue;

    /**
     * The parameter was BOOLEAN
     */
    public boolean isLogTrue() {
        return logvalue;
    }

    /**
     * The parameter was integer number, INT
     */
    public double getDoublevalue() {
        return doublevalue;
    }

    public Date getDatevalue() {
        return datevalue;
    }

    /**
     * The parameter was string (ny other) value, STRING
     */
    public String getStringvalue() {
        return stringvalue;
    }

    public int getIntvalue() {
        return intvalue;
    }

    ParamValue(boolean logvalue, double doublevalue, String stringvalue, Date datevalue, int intvalue) {
        this.logvalue = logvalue;
        this.doublevalue = doublevalue;
        this.stringvalue = stringvalue;
        this.datevalue = datevalue;
        this.intvalue = intvalue;
    }

    ParamValue() {
        this(false, -1, null, null, -1);
    }


    /**
     * BOOLEAN parameter
     */
    public ParamValue(boolean logvalue) {
        this(logvalue, -1, null, null, -1);
    }

    /**
     * DOUBLE parameter
     */
    public ParamValue(double dublevalue) {
        this(false, dublevalue, null, null, -1);

    }

    /**
     * STRING parameter
     */
    public ParamValue(String stringvalue) {
        this(false, -1, stringvalue, null, -1);
    }

    public ParamValue(Date datevalue) {
        this(false, -1, null, datevalue, -1);
    }

    public ParamValue(int intvalue) {
        this(false, -1, null, null, intvalue);
    }

}
