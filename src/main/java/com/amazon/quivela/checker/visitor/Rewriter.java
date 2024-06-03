/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.SymbolTable;
import com.amazon.quivela.checker.Unifier;
import com.amazon.quivela.checker.Util;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.*;

public class Rewriter extends DepthFirstAdapter {

    public static class Rewrite {
        public PExpr context;
        public PBoundsExpr distance;
    }

    private final PExpr left;
    private final Collection<String> vars;
    private SymbolTable symbolTable = new SymbolTable();
    private final PExpr right;
    private final PBoundsExpr distance;
    private final boolean rewriteInMethod;
    private final PExpr contextTop;

    private List<Rewrite> rewrites = new ArrayList();
    private PExpr topExpr = null;
    int methodDepth = 0;

    public Rewriter(PExpr left, Collection<String> vars, PExpr right, PBoundsExpr distance, boolean rewriteInMethod, PExpr contextTop) {
        this.left = left;
        this.vars = vars;
        this.right = right;
        this.distance = distance;
        this.rewriteInMethod = rewriteInMethod;
        this.contextTop = contextTop;
    }

    public Optional<PExpr> getTopExpr() {
        return Optional.ofNullable(topExpr);
    }

    public List<Rewrite> getRewrites() {
        return rewrites;
    }

    @Override
    public void inANewExpr(ANewExpr node) {
        symbolTable.pushFrame(false);
    }

    @Override
    public void outANewParam(ANewParam param) {
        symbolTable.addSymbol(param.getIdentifier().getText().trim(), null);
    }

    @Override
    public void outANewExpr(ANewExpr node) {

        symbolTable.popFrame();
    }

    @Override
    public void inAMethodDef(AMethodDef def) {
        methodDepth++;
        symbolTable.pushFrame(true);
    }

    @Override
    public void outAFormalParam(AFormalParam param) {
        IdentifierAndTypeExtractor ext = new IdentifierAndTypeExtractor(param.getIdentifierAndType());
        symbolTable.addSymbol(ext.getIdentifier(), null);
    }

    @Override
    public void outAMethodDef(AMethodDef def) {
        symbolTable.popFrame();
        methodDepth--;
    }

    @Override
    public void outAAssignAssignExpr(AAssignAssignExpr assign) {
        symbolTable.addSymbol(assign.getIdentifier().getText().trim(), null);
    }

    @Override
    public void outAExpr(AExpr node) {

        if(!rewriteInMethod && methodDepth > 0) {
            return;
        }

        Unifier unifier = new Unifier(left, vars, node, symbolTable.allSymbols());
        Map<String, Node> mgu = unifier.unify();
        if (mgu == null) {
            return;
        }

        Substituter sub = new Substituter(mgu);
        Node rightClone = (Node)right.clone();
        rightClone.apply(sub);
        if (sub.getNewTop() != null) {
            rightClone = sub.getNewTop();
        }

        Rewrite r = new Rewrite();
        sub = new Substituter(mgu);
        Node distanceClone = (Node)distance.clone();
        distanceClone.apply(sub);
        if (sub.getNewTop() != null) {
            distanceClone = sub.getNewTop();
        }
        r.distance = (PBoundsExpr)distanceClone;

        if (node.parent() == null) {
            r.context = null;
            topExpr = (AExpr)rightClone;
        } else {
            // replace with a hole to form rewrite context
            PExpr holeExpr = Util.toExpr(new AHolePrimaryExpr(new THole()));
            node.replaceBy(holeExpr);
            PExpr rewriteContext = (PExpr)contextTop.clone();
            r.context = rewriteContext;

            EnvContextReplacer envReplacer = new EnvContextReplacer(rewriteContext);
            r.distance.apply(envReplacer);

            holeExpr.replaceBy(rightClone);
        }

        rewrites.add(r);


    }


}
