/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.Util;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.Optional;

public class ExprSimplifier extends DepthFirstAdapter {

    private PExpr topExpr = null;

    public Optional<PExpr> getTopExpr() {
        return Optional.ofNullable(topExpr);
    }

    @Override
    public void outAInvokeExpr(AInvokeExpr node) {
        node.setInvokeClass(null);
    }

    @Override
    public void outAQuantPrimaryExpr(AQuantPrimaryExpr node) {
        node.setExprLabel(null);
    }

    @Override
    public void outANewExpr(ANewExpr node) {
        node.setClassIdent(null);
    }

    @Override
    public void outAExpr(AExpr expr) {
        Optional<AQuantPrimaryExpr> exprOpt = Util.toQuantPrimaryExpr(expr);
        if (exprOpt.isPresent()) {
            if (expr.parent() == null) {
                topExpr = exprOpt.get().getExpr();
            } else {
                expr.replaceBy(exprOpt.get().getExpr());
            }
        }
    }
}
