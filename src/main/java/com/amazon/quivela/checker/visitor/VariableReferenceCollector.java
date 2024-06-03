/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.SymbolTable;
import com.amazon.quivela.checker.Type;
import com.amazon.quivela.checker.visitor.IdentifierAndTypeExtractor;
import com.amazon.quivela.checker.visitor.LocalVariableDeclCollector;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.HashSet;
import java.util.Set;

public class VariableReferenceCollector extends DepthFirstAdapter {

    Set<String> idReferences = new HashSet<>();
    private final SymbolTable symbolTable;

    public VariableReferenceCollector() {
        this.symbolTable = new SymbolTable();
    }

    @Override
    public void inANewExpr(ANewExpr newExpr) {
        symbolTable.pushFrame(false);
    }

    @Override
    public void outANewExpr(ANewExpr newExpr) {
        symbolTable.popFrame();
    }

    @Override
    public void inANewParam(ANewParam newParam) {
        symbolTable.pushFrame(false);
        LocalVariableDeclCollector col = new LocalVariableDeclCollector(symbolTable);
        newParam.getExpr().apply(col);
    }

    @Override
    public void outANewParam(ANewParam newParam) {

        symbolTable.popFrame();

        TIdentifier idToken = newParam.getIdentifier();
        String id = idToken.getText().trim();
        symbolTable.addSymbol(id, Type.Opaque);
    }

    @Override
    public void inAMethodDef(AMethodDef method) {
        symbolTable.pushFrame(false);

        LocalVariableDeclCollector col = new LocalVariableDeclCollector(symbolTable);

        method.getFuncBody().apply(col);
    }

    @Override
    public void outAFormalParam(AFormalParam param) {
        IdentifierAndTypeExtractor ext = new IdentifierAndTypeExtractor(param.getIdentifierAndType());
        String id = ext.getIdentifier();
        // types are unused in this analysis
        symbolTable.addSymbol(id, null);
    }

    @Override
    public void outAMethodDef(AMethodDef method) {
        symbolTable.popFrame();
    }

    @Override
    public void inALookupPrimaryExpr(ALookupPrimaryExpr node) {
        String id = node.getIdentifier().getText();

        if (!symbolTable.varReferenceAllowed(id)) {
            idReferences.add(id);
        }
    }

    public Set getIds() {
        return idReferences;
    }
}
