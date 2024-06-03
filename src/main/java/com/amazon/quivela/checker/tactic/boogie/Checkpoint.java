/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

public class Checkpoint {

    private final String id;
    private final boolean isInvoke;

    public Checkpoint(String id, boolean isInvoke) {
        this.id = id;
        this.isInvoke = isInvoke;
    }

    public String getId() {
        return id;
    }

    public boolean isInvoke() {
        return isInvoke;
    }
}
