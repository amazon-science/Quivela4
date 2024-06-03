/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.ABoundsActualParams;
import com.amazon.quivela.parser.node.ABoundsExprs;
import com.amazon.quivela.parser.node.ABoundsExprsTl;
import com.amazon.quivela.parser.node.PBoundsExpr;

import java.util.ArrayList;
import java.util.List;

public class ActualParamsCollectorBounds extends AnalysisAdapter {

    private List<PBoundsExpr> exprs = new ArrayList();

    @Override
    public void caseABoundsActualParams(ABoundsActualParams node) {
        ABoundsExprs expr = (ABoundsExprs)node.getBoundsExprs();
        if (expr != null) {
            exprs.add((PBoundsExpr)expr.getBoundsExpr().clone());
            for (Object obj : expr.getBoundsExprsTl()) {
                ABoundsExprsTl tl = (ABoundsExprsTl) obj;
                exprs.add((PBoundsExpr)tl.getBoundsExpr().clone());
            }
        }
    }

    public List<PBoundsExpr> getExprs() {
        return exprs;
    }
}
