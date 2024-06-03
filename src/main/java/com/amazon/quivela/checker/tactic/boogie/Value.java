/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

public class Value {

    private final BoogieType t;
    private final String v;

    public Value(BoogieType t, String v) {
        this.t = t;
        this.v = v;
    }

    public BoogieType getType() {
        return t;
    }
    public String getValue() {
        return v;
    }

    public String getValue(BoogieType t) {
        if (t == this.t) {
            return v;
        } else {

            if (t == BoogieType.Opaque) {
                return toOpaqueString();
            } else if (t == BoogieType.Integer) {
                return "toInt(" + toBitstringString() + ")";
            } else if (t == BoogieType.Boolean) {
                return "toBool(" + toBitstringString() + ")";
            } else if (t == BoogieType.Real) {
                if (this.t == BoogieType.Integer) {
                    return "real(" + v + ")";
                } else {
                    return "toReal(" + toBitstringString() + ")";
                }
            } else if (t == BoogieType.Bitstring) {
                return toBitstringString();
            } else if (t == BoogieType.Memory) {
                return "toMemory(" + toOpaqueString() + ")";
            } else {
                throw new RuntimeException("Unknown type: " + t);
            }
        }
    }


    @Override
    public String toString() {
        throw new RuntimeException("No!");
    }

    public String toIntegerString() {
        if (t == BoogieType.Integer) {
            return v;
        } else {
            return "toInt(" + toBitstringString() + ")";
        }
    }

    public String toBooleanString() {
        if (t == BoogieType.Boolean) {
            return v;
        } else {
            return toBitstringString() + "!=nil";
        }
    }

    public String toOpaqueString() {
        if (t == BoogieType.Opaque) {
            return v;
        } else if (t == BoogieType.ObjectId) {
            return "fromObjectId(" + v + ")";
        } else if (t == BoogieType.Memory) {
            return "fromMemory(" + v + ")";
        } else if (t == BoogieType.Expr) {
            return "fromExpr(" + v + ")";
        } else {
            return "fromBitstring(" + toBitstringString() + ")";
        }
    }

    public String toBitstringString() {
        if (t == BoogieType.Bitstring) {
            return v;
        } else if (t == BoogieType.Opaque){
            return "toBitstring(" + v + ")";
        } else if (t == BoogieType.Boolean){
            return "fromBool(" + v + ")";
        } else if (t == BoogieType.Integer) {
            return "fromInt(" + v + ")";
        } else if (t == BoogieType.Real) {
            return "fromReal(" + v + ")";
        } else {
            throw new RuntimeException("Unsupported type: " + t);
        }
    }

    public String toObjectIdString() {
        if (t == BoogieType.ObjectId) {
            return v;
        } else {
            return "toObjectId(" + toOpaqueString() + ")";
        }
    }

    public String toMemoryString() {
        if (t == BoogieType.Object) {
            return v + "[objectMemoryAttr]";
        } else if (t == BoogieType.Memory) {
            return v;
        } else {
            return "toMemory(" + toOpaqueString() + ")";
        }
    }

    public String toRealString() {
        if (t == BoogieType.Real) {
            return v;
        } else if (t == BoogieType.Integer) {
            return "real(" + v + ")";
        } else {
            return "toReal(" + toBitstringString() + ")";
        }
    }
}
