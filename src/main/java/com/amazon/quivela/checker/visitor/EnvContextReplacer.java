/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayList;
import java.util.List;

public class EnvContextReplacer extends DepthFirstAdapter {

    private final PExpr context;

    public EnvContextReplacer(PExpr context) {
        this.context = context;
    }

    @Override
    public void outAEnvPrimaryBoundsExpr(AEnvPrimaryBoundsExpr node) {
        if (context != null) {

            if (node.getEnvParam() == null) {
                List<AEnvParam> params = new ArrayList();
                params.add(new AEnvParam(new TLPar(), (PExpr) context.clone(), new TRPar()));
                node.setEnvParam(params);
            } else {
                node.getEnvParam().add(0, new AEnvParam(new TLPar(), (PExpr) context.clone(), new TRPar()));
            }

        }
    }
}
