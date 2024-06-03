/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic;

import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.CheckException;
import com.amazon.quivela.checker.execution.ProofTaskConsumer;
import com.amazon.quivela.checker.SymbolTable;
import com.amazon.quivela.checker.tactic.boogie.*;
import com.amazon.quivela.parser.node.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/*
 The Auto tactic checks the equivalence of two expressions. It is intended for use in simple cases such as showing the equivalence
 of two programs after definitions have been unfolded.
 */

public class Auto {

    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final Collection<AAxiomDecl> axioms;
    private final Map<String, ANewExpr> identifiedClasses;
    private final ProofTaskConsumer taskConsumer;

    public Auto(ProofTaskConsumer taskConsumer, SymbolTable symbolTable, BoogieFunctions functions, Collection<AAxiomDecl> axioms, Map<String, ANewExpr> identifiedClasses) {
        this.taskConsumer = taskConsumer;
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.axioms = axioms;
        this.identifiedClasses = identifiedClasses;
    }

    // Check that left is equivalent to right
    public void check(File file, int line, int pos, PExpr left, PExpr right, String message) throws CheckException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream out = new PrettyPrintStream(baos);

        BoogieClasses classes = new BoogieClasses();
        BoogieMethods methods = new BoogieMethods();
        BoogieConstants constants = new BoogieConstants();

        BoogieUtil.writePrelude(out);
        BoogieUtil.writeSymbols(symbolTable, out);
        BoogieUtil.writeFuncDecls(symbolTable, functions, methods, classes, constants, out);
        BoogieUtil.writeAxioms(symbolTable, axioms, constants, identifiedClasses, functions, out);

        // write left procedure
        BoogieClassDecls classDecls = new BoogieClassDecls(symbolTable, functions, methods, classes, constants, out);
        left.apply(classDecls);
        ByteArrayOutputStream leftBaos = new ByteArrayOutputStream();
        PrettyPrintStream leftOut = new PrettyPrintStream(leftBaos);
        BoogieExprConverter leftExprConv = new BoogieExprConverter(symbolTable, functions, methods, classes, new HashMap(), new HashMap(), leftOut);
        left.apply(leftExprConv);
        String leftValue = leftExprConv.getValue();

        out.println("procedure {:inline 1} left(internal.objectId : ObjectId) returns (internal.r : T)");
        out.println("modifies checkpoints;");
        out.println("modifies functionState;");
        out.println("modifies heap; {");
        out.pushTab();

        for(String curVar : leftExprConv.getDeclaredVars().keySet()) {
            String type = leftExprConv.getDeclaredVars().get(curVar);
            out.println("var " + curVar + ":" + type + ";");
        }
        for(String curVar : leftExprConv.getDeclaredVars().keySet()) {
            String type = leftExprConv.getDeclaredVars().get(curVar);
            if (type.equals("T")) {
                out.println(curVar + " := defaultValue;");
            }
        }
        out.println(leftBaos.toString());
        out.println("internal.r := " + leftValue + ";");

        out.popTab();
        out.println("}");

        // write right procedure
        classDecls = new BoogieClassDecls(symbolTable, functions, methods, classes, constants, out);
        right.apply(classDecls);
        ByteArrayOutputStream rightBaos = new ByteArrayOutputStream();
        PrettyPrintStream rightOut = new PrettyPrintStream(rightBaos);
        BoogieExprConverter rightExprConv = new BoogieExprConverter(symbolTable, functions, methods, classes, new HashMap(), new HashMap(), rightOut);
        right.apply(rightExprConv);
        String rightValue = rightExprConv.getValue();

        out.println("procedure {:inline 1} right(internal.objectId : ObjectId) returns (internal.r : T)");
        out.println("modifies checkpoints;");
        out.println("modifies functionState;");
        out.println("modifies heap; {");
        out.pushTab();

        for(String curVar : rightExprConv.getDeclaredVars().keySet()) {
            String type = rightExprConv.getDeclaredVars().get(curVar);
            out.println("var " + curVar + ":" + type + ";");
        }
        for(String curVar : rightExprConv.getDeclaredVars().keySet()) {
            String type = rightExprConv.getDeclaredVars().get(curVar);
            if (type.equals("T")) {
                out.println(curVar + " := defaultValue;");
            }
        }
        out.println(rightBaos.toString());
        out.println("internal.r := " + rightValue + ";");

        out.popTab();
        out.println("}");


        // write both procedure
        out.println("procedure both(internal.objectId : ObjectId) returns (internal.r1:T, internal.r2:T, functionState1 : FunctionState, functionState2 : FunctionState)");
        out.println("ensures functionState1==functionState2;");
        out.println("ensures heap1==heap2;");
        out.println("ensures internal.r1==internal.r2; ");
        out.println("modifies checkpoints;");
        out.println("modifies functionState;");
        out.println("modifies heap1;");
        out.println("modifies heap2;");
        out.println("modifies heap; {");
        out.pushTab();

        out.println("var functionState_sav : FunctionState;");
        out.println("var heap_sav : Heap;");
        out.println("functionState_sav := functionState;");
        out.println("heap_sav := heap;");

        out.println("call internal.r1 := left(internal.objectId);");

        out.println("functionState1 := functionState;");
        out.println("heap1 := heap;");
        out.println("functionState := functionState_sav;");
        out.println("heap := heap_sav;");

        out.println("call internal.r2 := right(internal.objectId);");
        out.println("functionState2 := functionState;");
        out.println("heap2 := heap;");

        out.popTab();
        out.println("}");
        out.println();

        out.close();

        BoogieProofTask boogieTask = new BoogieProofTask(file, line, pos, baos.toString(), message, "Goal check failed.", 3);
        taskConsumer.add(boogieTask);
    }

}
