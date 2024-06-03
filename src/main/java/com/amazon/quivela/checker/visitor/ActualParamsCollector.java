/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.AActualParams;
import com.amazon.quivela.parser.node.AExprs;
import com.amazon.quivela.parser.node.AExprsTl;
import com.amazon.quivela.parser.node.PExpr;

import java.util.ArrayList;
import java.util.List;

public class ActualParamsCollector extends AnalysisAdapter {

    private List<PExpr> exprs = new ArrayList<PExpr>();

    @Override
    public void caseAActualParams(AActualParams node) {
        AExprs expr = (AExprs)node.getExprs();
        if (expr != null) {
            exprs.add((PExpr)expr.getExpr().clone());
            for (Object obj : expr.getExprsTl()) {
                AExprsTl tl = (AExprsTl) obj;
                exprs.add((PExpr)tl.getExpr().clone());
            }
        }
    }

    public List<PExpr> getExprs() {
        return exprs;
    }

}
