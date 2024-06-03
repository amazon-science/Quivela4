/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker;

import com.amazon.quivela.checker.visitor.ActualParamsCollector;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.*;

public class Unifier {

    private final Collection<String> leftVars;
    private final Collection<String> rightVars;
    private final List<NodePair> pairs = new ArrayList<>();
    private final List<Transform> transforms = new ArrayList<>();

    private abstract class Transform {
        public abstract boolean transform();
    }

    public class IdentifierCollector extends DepthFirstAdapter {

        Set<String> ids = new HashSet<>();

        @Override
        public void caseTIdentifier(TIdentifier id) {
            ids.add(id.getText().trim());
        }

        public Set<String> getIds() {
            return ids;
        }
    }


    private boolean leftRightEquals(Node left, Node right) {
        // left is only equal to right if they are syntactically equal and they contain no variables
        if (left==null && right==null) {
            return true;
        }

        if (!Util.equals(left, right)) {
            return false;
        }

        IdentifierCollector idCollector = new IdentifierCollector();
        left.apply(idCollector);
        Set<String> leftRefs = idCollector.getIds();
        for (String curRef : leftRefs) {
            if (leftVars.contains(curRef)) {
                return false;
            }
        }

        idCollector = new IdentifierCollector();
        left.apply(idCollector);
        Set<String> rightRefs = idCollector.getIds();
        for (String curRef : rightRefs) {
            if (rightVars.contains(curRef)) {
                return false;
            }
        }

        return true;

    }

    private class Delete extends Transform {
        public boolean transform() {

            for (int i = 0; i < pairs.size(); i++) {
                NodePair curPair = pairs.get(i);
                if(leftRightEquals(curPair.getLeft(), curPair.getRight())) {
                    pairs.remove(i);
                    return true;
                }

                // check for duplicates
                for (int j = 0; j < pairs.size(); j++) {
                    if (i != j) {
                        NodePair otherPair = pairs.get(j);
                        if (Util.equals(curPair.getLeft(), otherPair.getLeft()) &&
                            Util.equals(curPair.getRight(), otherPair.getRight())) {

                            pairs.remove(i);
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }

    private static class Decomposer extends AnalysisAdapter {

        private final Node other;
        private List<NodePair> pairs = null;

        public Decomposer(Node other) {
            this.other = other;
        }

        public Optional<List<NodePair>> getResult() {
            return Optional.ofNullable(pairs);
        }

        @Override
        public void defaultCase(Node node) {
            throw new RuntimeException("Decomposition on " + node.getClass().getName() + " not implemented.");
        }

        @Override
        public void caseAQuantPrimaryExpr(AQuantPrimaryExpr node) {
            if (!(other instanceof AQuantPrimaryExpr)) {
                return;
            }

            AQuantPrimaryExpr aOther = (AQuantPrimaryExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getExpr(), aOther.getExpr()));
            pairs.add(new NodePair(node.getExprLabel(), aOther.getExprLabel()));
        }

        @Override
        public void caseAExpr(AExpr node) {
            if (!(other instanceof AExpr)) {
                return;
            }

            AExpr aOther = (AExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getLogicExpr(), aOther.getLogicExpr()));
        }

        @Override
        public void caseAAssignExprLogicExpr(AAssignExprLogicExpr node)
        {
            if (!(other instanceof AAssignExprLogicExpr)) {
                return;
            }

            AAssignExprLogicExpr aOther = (AAssignExprLogicExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getAssignExpr(), aOther.getAssignExpr()));
        }

        @Override
        public void caseABoolAssignExpr(ABoolAssignExpr node)
        {
            if (!(other instanceof ABoolAssignExpr)) {
                return;
            }
            ABoolAssignExpr aOther = (ABoolAssignExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getBoolExpr(), aOther.getBoolExpr()));
        }
        @Override
        public void caseARelOpBoolExpr(ARelOpBoolExpr node)
        {
            if (!(other instanceof ARelOpBoolExpr)) {
                return;
            }

            ARelOpBoolExpr aOther = (ARelOpBoolExpr)other;
            if (!Util.equals(node.getRelOp(), aOther.getRelOp())) {
                return;
            }

            pairs = new ArrayList();
            pairs.add(new NodePair(node.getArithExpr(), aOther.getArithExpr()));
            pairs.add(new NodePair(node.getBoolExpr(), aOther.getBoolExpr()));

        }

        @Override
        public void caseAArithExpr(AArithExpr node) {

            if (!(other instanceof AArithExpr)) {
                return;
            }
            AArithExpr aOther = (AArithExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getSumExpr(), aOther.getSumExpr()));

        }

        @Override
        public void caseAProductSumExpr(AProductSumExpr node) {
            if (!(other instanceof AProductSumExpr)) {
                return;
            }
            AProductSumExpr aOther = (AProductSumExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getProductExpr(), aOther.getProductExpr()));

        }

        @Override
        public void caseAPrimaryExprProductExpr(APrimaryExprProductExpr node)
        {
            if (!(other instanceof APrimaryExprProductExpr)) {
                return;
            }
            APrimaryExprProductExpr aOther = (APrimaryExprProductExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getPrimaryExpr(), aOther.getPrimaryExpr()));
        }
        @Override
        public void caseAArithExprBoolExpr(AArithExprBoolExpr node)
        {
            if (!(other instanceof AArithExprBoolExpr)) {
                return;
            }
            AArithExprBoolExpr aOther = (AArithExprBoolExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getArithExpr(), aOther.getArithExpr()));
        }

        @Override
        public void caseALookupPrimaryExpr(ALookupPrimaryExpr node)
        {
            if (!(other instanceof ALookupPrimaryExpr)) {
                return;
            }
            ALookupPrimaryExpr aOther = (ALookupPrimaryExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getIdentifier(), aOther.getIdentifier()));
        }

        @Override
        public void caseARefPrimaryExpr(ARefPrimaryExpr node)
        {
            if (!(other instanceof ARefPrimaryExpr)) {
                return;
            }
            ARefPrimaryExpr aOther = (ARefPrimaryExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getExpr(), aOther.getExpr()));
        }

        @Override
        public void caseAFuncExprPrimaryExpr(AFuncExprPrimaryExpr node)
        {
            if (!(other instanceof AFuncExprPrimaryExpr)) {
                return;
            }
            AFuncExprPrimaryExpr aOther = (AFuncExprPrimaryExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getFuncExpr(), aOther.getFuncExpr()));
        }

        @Override
        public void caseAFuncExpr(AFuncExpr node)
        {
            if (!(other instanceof AFuncExpr)) {
                return;
            }
            AFuncExpr aOther = (AFuncExpr)other;
            if (!Util.equals(node.getIdentifier(), aOther.getIdentifier())) {
                return;
            }

            pairs = new ArrayList();
            ActualParamsCollector col = new ActualParamsCollector();
            node.getActualParams().apply(col);
            List<PExpr> leftExprs = col.getExprs();

            col = new ActualParamsCollector();
            aOther.getActualParams().apply(col);
            List<PExpr> rightExprs = col.getExprs();

            if (leftExprs.size() != rightExprs.size()) {
                throw new RuntimeException("Argument length mismatch for function " + node.getIdentifier());
            }

            for (int i = 0; i < leftExprs.size(); i++) {
                pairs.add(new NodePair(leftExprs.get(i), rightExprs.get(i)));
            }
        }

        @Override
        public void caseASequenceLogicExpr(ASequenceLogicExpr node)
        {
            if (!(other instanceof ASequenceLogicExpr)) {
                return;
            }
            ASequenceLogicExpr aOther = (ASequenceLogicExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getLeft(), aOther.getLeft()));
            pairs.add(new NodePair(node.getRight(), aOther.getRight()));
        }

        @Override
        public void caseAAssignAssignExpr(AAssignAssignExpr node)
        {
            if (!(other instanceof AAssignAssignExpr)) {
                return;
            }
            AAssignAssignExpr aOther = (AAssignAssignExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getIdentifier(), aOther.getIdentifier()));
            pairs.add(new NodePair(node.getAssignValue(), aOther.getAssignValue()));
        }

        @Override
        public void caseAAssignAssignValue(AAssignAssignValue node)
        {
            if (!(other instanceof AAssignAssignValue)) {
                return;
            }
            AAssignAssignValue aOther = (AAssignAssignValue)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getBoolExpr(), aOther.getBoolExpr()));
        }

        @Override
        public void caseAMapLookupPrimaryExpr(AMapLookupPrimaryExpr node)
        {
            if (!(other instanceof AMapLookupPrimaryExpr)) {
                return;
            }
            AMapLookupPrimaryExpr aOther = (AMapLookupPrimaryExpr)other;
            pairs = new ArrayList();
            pairs.add(new NodePair(node.getMap(), aOther.getMap()));
            pairs.add(new NodePair(node.getIndex(), aOther.getIndex()));
        }

        @Override
        public void caseTIdentifier(TIdentifier node)
        {
            // no nothing
        }
    }

    private class Decompose extends Transform {
        public boolean transform() {
            for (int i = 0; i < pairs.size(); i++) {
                NodePair curPair = pairs.get(i);
                Decomposer dec = new Decomposer(curPair.getRight());
                curPair.getLeft().apply(dec);
                if (dec.getResult().isPresent()) {
                    pairs.remove(i);
                    pairs.addAll(dec.getResult().get());
                    return true;
                }

            }

            return false;
        }
    }

    private static class NodePair {

        private final Node left;
        private final Node right;

        public NodePair(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        public Node getLeft() {
            return left;
        }
        public Node getRight() {
            return right;
        }
    }

    public Unifier(Node left, Collection<String> leftVars, Node right, Collection<String> rightVars) {
        this.leftVars = leftVars;
        this.rightVars = rightVars;
        pairs.add(new NodePair(left, right));

        transforms.add(new Delete());
        transforms.add(new Decompose());
    }

    private static String getIdentifier(Node n) {

        if (n instanceof TIdentifier) {
            TIdentifier id = (TIdentifier)n;
            return id.getText().trim();
        } else if (n instanceof ALookupPrimaryExpr) {
            ALookupPrimaryExpr expr = (ALookupPrimaryExpr)n;
            return getIdentifier(expr.getIdentifier());
        } else if (n instanceof APrimaryExprProductExpr) {
            APrimaryExprProductExpr expr = (APrimaryExprProductExpr)n;
            return getIdentifier(expr.getPrimaryExpr());
        } else if (n instanceof AProductSumExpr) {
            AProductSumExpr expr = (AProductSumExpr)n;
            return getIdentifier(expr.getProductExpr());
        }
        return null;
    }

    private Map<String, Node> getSolvedForm() {
        Map<String, Node> result = new HashMap();
        for(NodePair curPair : pairs) {

            String leftIdent = getIdentifier(curPair.getLeft());
            if (leftIdent == null) {
                return null;
            }
            if (!leftVars.contains(leftIdent)) {
                return null;
            }
            if (result.containsKey(leftIdent)) {
                return null;
            }
            result.put(leftIdent, curPair.getRight());

        }

        // Due to constraints on unification, right terms contain no variables, so we are done

        return result;
    }

    private boolean transformOnce() {
        for (Transform curTransform : transforms) {
            if (curTransform.transform()) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Node> unify() {

        Map<String, Node> solved = getSolvedForm();

        while(solved == null) {
            if (! transformOnce()) {
                return null;
            }
            solved = getSolvedForm();
        }

        return solved;
    }
}
