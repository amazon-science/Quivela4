/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.Map;

public class VarSubstituter extends DepthFirstAdapter {

    private Map<String, String> subs;
    private int disableLevel = 0;

    public VarSubstituter(Map<String, String> subs) {
        this.subs = subs;
    }

    @Override
    public void inANewExpr(ANewExpr node) {
        node.getNewParamsList().apply(this);
        ++disableLevel;
    }

    @Override
    public void outANewExpr(ANewExpr node) {
        --disableLevel;
    }

    @Override
    public void outALookupPrimaryExpr(ALookupPrimaryExpr node) {
        if (disableLevel != 0) {
            return;
        }
        String idString = node.getIdentifier().getText();
        String sub = subs.get(idString);
        if (sub != null) {
            node.replaceBy(new ALookupPrimaryExpr(new TIdentifier(sub)));
        }
    }

    @Override
    public void outAAssignAssignExpr(AAssignAssignExpr node) {
        if (disableLevel != 0) {
            return;
        }

        String idString = node.getIdentifier().getText();
        String sub = subs.get(idString);
        if (sub != null) {
            node.getIdentifier().replaceBy(new TIdentifier(sub));
        }
    }
}