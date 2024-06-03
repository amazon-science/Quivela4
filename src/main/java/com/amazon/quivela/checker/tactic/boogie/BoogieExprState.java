/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

public class BoogieExprState {

    private final String heap;
    private final String objectMemory;
    private final String value;

    public BoogieExprState(String heap, String objectMemory, String value) {
        this.heap = heap;
        this.objectMemory = objectMemory;
        this.value = value;
    }

    public String getHeap() {
        return heap;
    }

    public String getObjectMemory() {
        return objectMemory;
    }

    public String getValue() {
        return value;
    }
}
