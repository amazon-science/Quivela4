/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic;

import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.execution.ProofTaskConsumer;
import com.amazon.quivela.checker.SymbolTable;
import com.amazon.quivela.checker.tactic.boogie.*;
import com.amazon.quivela.parser.node.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collection;
import java.util.Map;

/*
 The BoundsAuto tactic checks the validity of bounds expressions.
 */

public class BoundsAuto {

    private final ProofTaskConsumer taskConsumer;
    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final Collection<AAxiomDecl> axioms;
    private final Map<String, ANewExpr> identifiedClasses;

    public BoundsAuto(ProofTaskConsumer taskConsumer, SymbolTable symbolTable, BoogieFunctions functions, Collection<AAxiomDecl> axioms, Map<String, ANewExpr> identifiedClasses) {
        this.taskConsumer = taskConsumer;
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.axioms = axioms;
        this.identifiedClasses = identifiedClasses;
    }

    // Check the actual <= max
    public void check(File file, int line, int pos, PBoundsExpr actual, PBoundsExpr max) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream out = new PrettyPrintStream(baos);

        BoogieClasses classes = new BoogieClasses();
        BoogieMethods methods = new BoogieMethods();
        BoogieConstants constants = new BoogieConstants();

        BoogieUtil.writePrelude(out);
        BoogieUtil.writeSymbols(symbolTable, out);
        BoogieUtil.writeFuncDecls(symbolTable, functions, methods, classes, constants, out);
        BoogieUtil.writeAxioms(symbolTable, axioms, constants, identifiedClasses, functions, out);

        ByteArrayOutputStream leftBaos = new ByteArrayOutputStream();
        PrettyPrintStream leftOut = new PrettyPrintStream(leftBaos);
        BoogieBoundsExprConverter leftExprConv = new BoogieBoundsExprConverter(symbolTable, functions, constants, out);
        actual.apply(leftExprConv);
        String leftValue = leftExprConv.getValue().toRealString();

        ByteArrayOutputStream rightBaos = new ByteArrayOutputStream();
        PrettyPrintStream rightOut = new PrettyPrintStream(rightBaos);
        BoogieBoundsExprConverter rightExprConv = new BoogieBoundsExprConverter(symbolTable, functions, constants, out);
        max.apply(rightExprConv);
        String rightValue = rightExprConv.getValue().toRealString();

        out.println("procedure {:inline 1} left() returns (internal.r : real)");
        out.println("{");
        out.pushTab();

        out.println(leftBaos.toString());
        out.println("internal.r := " + leftValue + ";");

        out.popTab();
        out.println("}");

        out.println("procedure {:inline 1} right() returns (internal.r : real)");
        out.println("{");
        out.pushTab();

        out.println(rightBaos.toString());
        out.println("internal.r := " + rightValue + ";");

        out.popTab();
        out.println("}");


        // write both procedure
        out.println("procedure both() returns (internal.r1:real, internal.r2:real)");
        out.println("ensures internal.r1<=internal.r2; ");
        out.println("{");
        out.pushTab();


        out.println("call internal.r1 := left();");
        out.println("call internal.r2 := right();");

        out.popTab();
        out.println("}");
        out.println();

        out.close();

        BoogieProofTask boogieTask = new BoogieProofTask(file, line, pos, baos.toString(), "Checking bounds", "Bounds check failed: cannot prove that " + actual.toString() + "\n <= \n" + max.toString(), 4);
        taskConsumer.add(boogieTask);

    }

}
