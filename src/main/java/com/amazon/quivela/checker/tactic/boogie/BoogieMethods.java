/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BoogieMethods {

    private Map<String, String> methodsMap = new HashMap();

    public String getBoogieProcName(String methodName) {
        return methodsMap.get(methodName);
    }

    public Collection<String> getBoogieProcNames() {
        return methodsMap.values();
    }

    public void put(String name, String boogieProcName) {
        methodsMap.put(name, boogieProcName);
    }

    public String freshMethodProcName(String methodName) {
        for(int i = 0; ; i++) {
            String procName = methodName + ".proc." + i;
            if (!getBoogieProcNames().contains(procName)) {
                return procName;
            }
        }
    }
}
