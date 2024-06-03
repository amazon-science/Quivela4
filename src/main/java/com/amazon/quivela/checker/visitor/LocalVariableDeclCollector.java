/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.SymbolTable;
import com.amazon.quivela.checker.Type;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.AAssignAssignExpr;

public class LocalVariableDeclCollector extends DepthFirstAdapter {

    private final SymbolTable symbolTable;

    public LocalVariableDeclCollector(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    @Override
    public void outAAssignAssignExpr(AAssignAssignExpr assignExpr) {
        String id = assignExpr.getIdentifier().getText().trim();
        if (!symbolTable.varReferenceAllowed(id)) {
            symbolTable.addSymbol(id, Type.Opaque);
        }
    }
}
