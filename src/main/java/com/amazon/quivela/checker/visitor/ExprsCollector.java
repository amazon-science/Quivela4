/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.AExprs;
import com.amazon.quivela.parser.node.AExprsTl;
import com.amazon.quivela.parser.node.PExpr;

import java.util.ArrayList;
import java.util.List;

public class ExprsCollector extends AnalysisAdapter {

    private List<PExpr> exprs = new ArrayList<PExpr>();

    @Override
    public void caseAExprs(AExprs node) {

        exprs.add(node.getExpr());

        for (Object curExprObj : node.getExprsTl()) {
            AExprsTl curExpr = (AExprsTl)curExprObj;
            exprs.add(curExpr.getExpr());
        }
    }

    public List<PExpr> getExprs() {
        return exprs;
    }

}
