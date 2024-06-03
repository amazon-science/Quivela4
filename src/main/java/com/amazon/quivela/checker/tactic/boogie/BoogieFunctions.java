/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.parser.node.AFuncDecl;

import java.util.*;

public class BoogieFunctions {

    private Map<String, AFuncDecl> functionsByName = new HashMap();
    private List<String> names = new ArrayList();

    public Collection<String> getNames() {
        return names;
    }

    public AFuncDecl getByName(String name) {
        return functionsByName.get(name);
    }

    public void put(String name, AFuncDecl def) {
        if (functionsByName.get(name) != null ) {
            throw new RuntimeException("Internal error. Function redefinition: " + name);

        }

        functionsByName.put(name, def);
        names.add(name);
    }


}
