/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.visitor.ActualParamsCollector;
import com.amazon.quivela.checker.visitor.FormalParamsCollector;
import com.amazon.quivela.checker.visitor.NewParamsCollector;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class BoogieClassDecls extends DepthFirstAdapter {

    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final BoogieMethods methods;
    private final BoogieClasses classes;
    private final BoogieConstants constants;
    private final PrettyPrintStream out;

    public BoogieClassDecls(SymbolTable symbolTable, BoogieFunctions functions, BoogieMethods methods, BoogieClasses classes, BoogieConstants constants, PrettyPrintStream out) {
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.methods = methods;
        this.classes = classes;
        this.constants = constants;
        this.out = out;
    }

    private String freshMethodProcName(String methodName) {
        return methods.freshMethodProcName(methodName);
    }

    @Override
    public void inAFuncDecl(AFuncDecl node) {
        symbolTable.pushFrame(true);
        FormalParamsCollector col = new FormalParamsCollector();
        node.getFormalParamsList().apply(col);
        symbolTable.addAll(col.getIds(), Type.Opaque);
    }

    @Override
    public void outAFuncDecl(AFuncDecl node) {
        symbolTable.popFrame();
    }

    @Override
    public void outAInvokeExpr(AInvokeExpr invokeExpr) {
        // ensure all invoked methods have an abstract definition
        // future method definitions with the same name can refine this definition with dispatch procedures

        String methodName = invokeExpr.getIdentifier().getText().trim();
        String boogieProcName = methods.getBoogieProcName(methodName);
        if (boogieProcName == null) {
            boogieProcName = freshMethodProcName(methodName);
            ActualParamsCollector col = new ActualParamsCollector();
            invokeExpr.getActualParams().apply(col);

            BoogieUtil.declareTargetMethod(boogieProcName, col.getExprs().size(), out);
            methods.put(methodName, boogieProcName);
        }
    }

    @Override
    public void outANewExpr(ANewExpr newExpr) {

        if (classes.getByDef(newExpr) != null) {
            return;
        }

        String className = classes.freshClassId();
        if (newExpr.getClassIdent() != null) {
            AClassIdent classIdent = (AClassIdent)newExpr.getClassIdent();
            className = classIdent.getIdentifier().getText().trim();
        }

        out.println("const unique " + className + " : ClassId;");

        ByteArrayOutputStream objOutBaos = new ByteArrayOutputStream();
        PrettyPrintStream objOut = new PrettyPrintStream(objOutBaos);
        BoogieObjectConverter objConverter = new BoogieObjectConverter(symbolTable, functions, methods, classes, constants, className, objOut, className);
        newExpr.apply(objConverter);

        // Object converter does not visit exprs in new params, which we need to produce declarations
        NewParamsCollector paramsCollector = new NewParamsCollector();
        newExpr.getNewParamsList().apply(paramsCollector);
        for(ANewParam curParam : paramsCollector.getParams()) {
            ByteArrayOutputStream newParamOutBaos = new ByteArrayOutputStream();
            PrettyPrintStream newParamOut = new PrettyPrintStream(newParamOutBaos);
            BoogieExprConverter exprConv = new BoogieExprConverter(symbolTable, functions,  methods,  classes, new HashMap(), new HashMap(), newParamOut);
            curParam.getExpr().apply(exprConv);
            for (Checkpoint curCheckpoint : exprConv.getCheckpoints().values()) {
                constants.addCheckpoint(curCheckpoint.getId(), out);
            }
        }

        classes.put(newExpr, className);

        constants.addFields(objConverter.getFields(), out);
        for(String methodName : objConverter.getMethodSigs().keySet()) {
            constants.addMethod(methodName, out);
        }
        for(Checkpoint curCheckpoint : objConverter.getCheckpoints().values()) {
            constants.addCheckpoint(curCheckpoint.getId(), out);
        }

        out.println(objOutBaos.toString());

    }

}
