/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker;

import java.util.*;

public class SymbolTableFrame {

    public enum Kind {
        CONSTANT,
        MUTABLE,
        LOGICAL
    }

    private Kind kind;
    private Map<String, Type> ids = new HashMap();

    public SymbolTableFrame(Kind kind) {
        this.kind = kind;
    }

    public void add(String id, Type type) {
        ids.put(id, type);
    }
    public boolean contains(String id) {
        return ids.containsKey(id);
    }
    public Kind getKind() {
        return kind;
    }
    public void addAll(SymbolTableFrame other) {
        for(String curId : other.ids.keySet()) {
            ids.put(curId, other.ids.get(curId));
        }
    }
    public Collection<String> getIds() {
        return ids.keySet();
    }

    public Type getType(String id) {
        return ids.get(id);
    }
}