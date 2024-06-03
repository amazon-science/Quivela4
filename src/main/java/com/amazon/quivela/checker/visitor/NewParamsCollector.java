/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayList;
import java.util.List;

public class NewParamsCollector extends AnalysisAdapter {

    private List<ANewParam> params = new ArrayList<ANewParam>();

    @Override
    public void caseANewParamsList(ANewParamsList node) {
        if (node.getNewParams() != null) {
            node.getNewParams().apply(this);
        }
    }

    @Override
    public void caseANewParams(ANewParams node) {
        ANewParam param = (ANewParam)node.getNewParam();
        if (param != null) {
            params.add((ANewParam)param.clone());
            for (Object obj : node.getNewParamsTl()) {
                ANewParamsTl tl = (ANewParamsTl) obj;
                params.add((ANewParam)tl.getNewParam().clone());
            }
        }
    }

    @Override
    public void caseANewExpr(ANewExpr expr) {
        expr.getNewParamsList().apply(this);
    }

    public List<ANewParam> getParams() {
        return params;
    }

}