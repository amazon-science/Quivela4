/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.parser.node.AMethodDef;

import java.util.*;

public class BoogieConstants {

    private Set<String> fields = new HashSet();
    private Set<String> checkpoints = new HashSet();
    private Map<String, String> methods = new HashMap();
    private Set<String> methodNames = new HashSet();
    private Map<String, String> exprs = new HashMap();
    private int methodProcIdCtr = 0;

    public void addFields(Collection<String> newFields, PrettyPrintStream out) {
        for (String curField : newFields) {
            if(!fields.contains(curField)) {

                out.println("const unique internal.attribute.field." + curField + " : Bitstring;");

                fields.add(curField);
            }
        }
    }

    public void addCheckpoint(String checkpointName, PrettyPrintStream out) {
        if (!checkpoints.contains(checkpointName)) {
            String checkpointId = BoogieUtil.toCheckpointId(checkpointName);
            out.println("const unique " + checkpointId + " : CheckpointId;");

            checkpoints.add(checkpointName);
        }

    }

    public void addMethodName(String methodName, PrettyPrintStream out) {
        if (!methodNames.contains(methodName)) {
            methodNames.add(methodName);
        }
    }

    public String addExpr(String expr, PrettyPrintStream out) {

        String existingConst = exprs.get(expr);
        if (existingConst != null) {
            return existingConst;
        }

        String constId = BoogieUtil.freshExprConstId(exprs.values());
        exprs.put(expr, constId);
        out.println("const unique " + constId + ":Expr;");
        return constId;
    }


    public String getMethodRef(String methodName) {
        return "internal.attribute.method." + methodName;
    }

    public void addMethod(String methodDef, PrettyPrintStream out) {

        if (!methods.containsKey(methodDef)) {

            String methodName = "m" + methodProcIdCtr++;
            String procId = "internal.methodProcId." + methodName;
            methods.put(methodDef, procId);
        }
    }

    public String getMethodProcId(String methodDef) {
        return methods.get(methodDef);
    }

    public String getMethodProcId(AMethodDef methodDef) {
        return getMethodProcId(methodDef.toString().trim());
    }
}
