/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.Util;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.Map;
import java.util.Optional;

public class Substituter extends DepthFirstAdapter {

    private Map<String, Node> subs;
    private Node newTop = null;

    public Substituter(Map<String, Node> subs) {
        this.subs = subs;
    }

    @Override
    public void outALookupPrimaryExpr(ALookupPrimaryExpr node) {
        String idString = node.getIdentifier().getText();
        Node sub = subs.get(idString);
        if (sub != null) {
            if (sub instanceof TIdentifier) {
                node.getIdentifier().replaceBy((TIdentifier)sub.clone());
            } else {
                if (node.parent() == null) {
                    newTop = (Node) sub.clone();
                } else {
                    Optional<AExpr> parentExprOpt = Util.getParent(node, AExpr.class);
                    parentExprOpt.get().replaceBy(Util.toExpr((Node)sub.clone()));
                }
            }
        }
    }

    @Override
    public void outALookupPrimaryBoundsExpr(ALookupPrimaryBoundsExpr node) {
        String idString = node.getIdentifier().getText();
        Node sub = subs.get(idString);
        if (sub != null) {
            if (sub instanceof TIdentifier) {
                node.getIdentifier().replaceBy((TIdentifier)sub.clone());
            } else if (sub instanceof ALiteralPrimaryExpr) {
                ALiteralPrimaryExpr literalExpr = (ALiteralPrimaryExpr)sub;
                node.replaceBy(new ALiteralPrimaryBoundsExpr(literalExpr.getLiteral()));
            } else if (sub instanceof ALookupPrimaryExpr) {
                ALookupPrimaryExpr lookupExpr = (ALookupPrimaryExpr)sub;
                node.replaceBy(new ALookupPrimaryBoundsExpr(lookupExpr.getIdentifier()));
            }else {
                node.replaceBy(Util.toPrimaryBoundsExpr(Util.toBoundsExpr(Util.exprNodeToBoundsExprNode((Node)sub.clone()))));
            }
        }
    }

    @Override
    public void outAAssignAssignExpr(AAssignAssignExpr node) {
        String idString = node.getIdentifier().getText();
        Node sub = subs.get(idString);
        if (sub != null) {
            node.getIdentifier().replaceBy(sub);
        }
    }

    public Node getNewTop() {
        return newTop;
    }
}