/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

public enum BoogieType {
    Boolean("Bool"),
    Opaque("T"),
    Integer("int"),
    ObjectId("ObjectID"),
    Object("Object"),
    Memory("Memory"),
    Heap("Heap"),
    Real("real"),
    Expr("Expr"),
    Bitstring("Bitstring");

    private final String boogieString;

    BoogieType(String boogieString) {
        this.boogieString = boogieString;
    }

    public String getBoogieString() {
        return boogieString;
    }
}