/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.Type;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

public class IdentifierAndTypeExtractor extends AnalysisAdapter {

    private String id = null;
    private TIdentifier idToken = null;
    private Type type = null;

    public IdentifierAndTypeExtractor(PIdentifierAndType idAndType) {
        idAndType.apply(this);
    }

    public IdentifierAndTypeExtractor() {

    }

    public String getIdentifier() {
        return id;
    }
    public TIdentifier getIdentifierToken() {
        return idToken;
    }
    public Type getType() {
        return type;
    }

    @Override
    public void caseAIdentifierAndType (AIdentifierAndType node) {
        idToken = node.getIdentifier();
        id = idToken.getText().trim();

        if (node.getTypeSuffix() != null) {
            node.getTypeSuffix().apply(this);
        }
    }

    @Override
    public void caseATypeSuffix(ATypeSuffix node) {
        node.getTypeExpr().apply(this);
        if (type == null) {
            throw new RuntimeException("unsupported type expression: " + node.getTypeExpr().toString());
        }
    }

    @Override
    public void caseAIntTypeExpr(AIntTypeExpr node) {
        type = Type.Integer;
    }

    @Override
    public void caseABitsTypeExpr(ABitsTypeExpr node) {
        type = Type.Bitstring;
    }

    @Override
    public void caseARealTypeExpr(ARealTypeExpr node) {
        type = Type.Real;
    }

    @Override
    public void caseAMapTypeExpr(AMapTypeExpr node) {
        type = Type.Map;
    }

    @Override
    public void caseAExprTypeExpr(AExprTypeExpr node) {
        type = Type.Expr;
    }


}
