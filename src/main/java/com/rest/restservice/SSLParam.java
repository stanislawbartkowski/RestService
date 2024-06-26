package com.rest.restservice;

/*
 * Copyright 2024 stanislawbartkowski@gmail.com
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Helper class, reads SSL parameters from property file
 */
public class SSLParam {

    private static final String STOREKEY = "store.key.filename";
    private static final String STOREPASSWORD = "key.store.password";

    private static String getParam(Properties prop, String key) throws IOException {
        String res = prop.getProperty(key);
        if (res == null || "".equals(res)) {
            String mess = "Parameter " + key + " not found in the secure property file";
            RestLogger.L.severe(mess);
            throw new IOException(mess);
        }
        return res;
    }

    /**
     * Reads and prepare array of SSL parameters from property file
     * @param filename Property file name
     * @return String array containing properties in accepted order
     * @throws IOException
     */
    public static String[] readConf(String filename) throws IOException {
        try (InputStream input = new FileInputStream(filename)) {
            Properties prop = new Properties();
            prop.load(input);
            return new String[]{
                    getParam(prop, STOREKEY),
                    getParam(prop, STOREPASSWORD)
            };
        }
    }

}
