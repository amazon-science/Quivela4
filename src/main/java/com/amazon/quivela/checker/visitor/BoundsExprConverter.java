/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayDeque;
import java.util.Deque;

public class BoundsExprConverter extends AnalysisAdapter {

    private Deque<Node> stack = new ArrayDeque();

    @Override
    public void defaultCase(Node node)
    {
        throw new RuntimeException("Conversion of " + node.getClass().getCanonicalName() + " not implemented.");
    }
    
    public PExpr getExpr() {
        return (PExpr)pop();
    }

    private void push(Node n) {
        stack.push(n);
    }
    private Node pop() {
        return stack.pop();
    }

    @Override
    public void caseABoundsExpr(ABoundsExpr node) {
        node.getSumBoundsExpr().apply(this);
        PSumExpr n = (PSumExpr)pop();
        push(new AExpr(new AAssignExprLogicExpr(new ABoolAssignExpr(new AArithExprBoolExpr(new AArithExpr(n))))));
    }

    @Override
    public void caseAProductBoundsExprSumBoundsExpr(AProductBoundsExprSumBoundsExpr node) {
        node.getProductBoundsExpr().apply(this);
        PProductExpr n = (PProductExpr)pop();
        push(new AProductSumExpr(n));
    }

    @Override
    public void caseAExponentBoundsExprProductBoundsExpr(AExponentBoundsExprProductBoundsExpr node) {
        node.getExponentBoundsExpr().apply(this);
        PPrimaryExpr n = (PPrimaryExpr)pop();
        push(new APrimaryExprProductExpr(n));
    }

    @Override
    public void caseAPrimaryBoundsExprExponentBoundsExpr(APrimaryBoundsExprExponentBoundsExpr node) {
        node.getPrimaryBoundsExpr().apply(this);
        // do nothing---exprs cannot have exponents
    }
    
    @Override
    public void caseALiteralPrimaryBoundsExpr(ALiteralPrimaryBoundsExpr node) {
        push(new ALiteralPrimaryExpr(node.getLiteral()));
    }

    @Override
    public void caseALookupPrimaryBoundsExpr(ALookupPrimaryBoundsExpr node) {
        push(new ALookupPrimaryExpr(node.getIdentifier()));
    }
}
