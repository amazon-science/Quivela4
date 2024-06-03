/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic;

import com.amazon.quivela.checker.*;
import com.amazon.quivela.checker.execution.ProofTaskConsumer;
import com.amazon.quivela.checker.tactic.boogie.BoogieFunctions;
import com.amazon.quivela.checker.visitor.ActualParamsCollector;
import com.amazon.quivela.checker.visitor.FormalParamsCollector;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.io.File;
import java.util.*;

/*
 The Unfold tactic unfolds definitions in an expression. When unfolding causes variables to be captured,
 the unfolded expression may not be equivalent to the original. Equivalence is checked by giving the
 original expression and the unfolded expression to the Auto tactic.
 */

public class Unfold {

    private File file;
    private ProofTaskConsumer taskConsumer;
    private BoogieFunctions funcTable;
    private List<CheckException> exceptions = new ArrayList<CheckException>();
    private SymbolTable symbolTable;

    public Unfold(File file, ProofTaskConsumer taskConsumer, SymbolTable symbolTable, BoogieFunctions funcTable) {
        this.file=file;
        this.taskConsumer = taskConsumer;
        this.symbolTable = symbolTable;
        this.funcTable = funcTable;
    }

    // Unfold definitions in the expression left and check for equivalence.
    public void transform(Collection<String> ids, PExpr left, int line, int pos) throws CheckException {

        // the unfolder will produce an unfolded expression that may not be equivalen
        // check result using Auto tactic
        PExpr leftIn = (PExpr)left.clone();
        Unfolder unfolder = new Unfolder(ids);
        left.apply(unfolder);
        if(!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }

        Auto auto = new Auto(taskConsumer, symbolTable, funcTable, new HashSet(), new HashMap());
        auto.check(file, line, pos, leftIn, left, "Checking unfold equivalence");

    }

    class Unfolder extends DepthFirstAdapter {

        private Collection<String> ids;

        public Unfolder(Collection<String> ids){
            this.ids = ids;
        }

        @Override
        public void outAFuncExpr(AFuncExpr node) {
            String funcName = node.getIdentifier().getText();
            if (ids != null && !ids.contains(funcName)) {
                return;
            }
            AFuncDecl func = funcTable.getByName(funcName);
            if (func == null) {
                exceptions.add(new CheckException(file,
                        node.getIdentifier(),
                        "function does not exist: " + funcName));
            } else {
                Optional<PExpr> subbedExpr = subParams(func, node);
                subbedExpr.ifPresent(e -> node.parent().replaceBy(Util.toPrimaryExpr(e)));
            }

        }

        private Optional<PExpr> subParams(AFuncDecl decl, AFuncExpr funcExpr) {

            FormalParamsCollector coll = new FormalParamsCollector();
            List<String> formalParams = new ArrayList<String>();
            if (decl.getFormalParamsList() != null) {
                decl.getFormalParamsList().apply(coll);
                formalParams = coll.getIds();
            }

            ActualParamsCollector aColl = new ActualParamsCollector();
            funcExpr.getActualParams().apply(aColl);
            List<PExpr> actualParams = aColl.getExprs();

            Map<String, PPrimaryExpr> subs = new HashMap<String, PPrimaryExpr>();
            for(int i = 0; i < formalParams.size(); i++) {
                String formalParam = formalParams.get(i);
                PPrimaryExpr expr = Util.defaultPrimaryExpr();
                if (i < actualParams.size()) {
                    expr = Util.toPrimaryExpr(actualParams.get(i));
                }
                subs.put(formalParam, expr);
            }

            Substituter sub = new Substituter(subs);
            if (decl.getFuncBody() != null) {
                PExpr bodyExpr = ((AFuncBody)decl.getFuncBody()).getExpr();
                PExpr body = (PExpr)bodyExpr.clone();
                body.apply(sub);
                // unfolding is only allowed if each parameter is used exactly once, and all uses are unconditional
                if (formalParams.size() == 0 || sub.getMaxNumSubs() == 1 && sub.getMinNumSubs() == 1 && !sub.getConditional()) {
                    return Optional.of(body);
                }
            }

            return Optional.empty();
        }

    }

    class Substituter extends DepthFirstAdapter {

        private Map<String, PPrimaryExpr> subs;
        private Map<String, Integer> numSubs = new HashMap<String, Integer>();
        private boolean conditional = false;
        private int disableLevel = 0;
        private int conditionalLevel = 0;

        public Substituter(Map<String, PPrimaryExpr> subs) {
            this.subs = subs;
        }

        @Override
        public void inALogicOpLogicExpr(ALogicOpLogicExpr node) {
            node.getLeft().apply(this);
            boolean incrConditional = false;
            switch(node.getLogicOp().getText()) {
                case "|" :
                case "&" :
                    incrConditional = true;
                case ";":
                    break;
                default :
                    throw new RuntimeException(new CheckException(file, node.getLogicOp(), "Unexpected logical operation: " + node.getLogicOp().getText()));
            }
            if (incrConditional) {
                ++conditionalLevel;
            }
            node.getRight().apply(this);
            if (incrConditional) {
                --conditionalLevel;
            }

            ++disableLevel;
        }

        public void outALogicOpLogicExpr(ALogicOpLogicExpr node) {
            --disableLevel;
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
            PPrimaryExpr sub = subs.get(idString);
            if (sub != null) {
                if (conditionalLevel != 0) {
                    conditional = true;
                }
                node.replaceBy(sub);
                Integer nSubs = numSubs.get(idString);
                if (nSubs == null) {
                    nSubs = 0;
                }
                numSubs.put(idString, nSubs + 1);
            }
        }

        public int getMaxNumSubs() {
            int max = 0;
            for(String key : numSubs.keySet()) {
                Integer curSubs = numSubs.get(key);
                if (curSubs == null) {
                    curSubs = 0;
                }
                max = Math.max(max, curSubs);
            }

            return max;
        }

        public int getMinNumSubs() {
            Integer min = null;
            for(String key : numSubs.keySet()) {
                Integer curSubs = numSubs.get(key);
                if (curSubs == null) {
                    curSubs = 0;
                }
                if (min == null) {
                    min = curSubs;
                } else {
                    min = Math.min(min, curSubs);
                }
            }

            if (min == null) {
                return 0;
            } else {
                return min;
            }
        }
        public boolean getConditional() {
            return conditional;
        }
    }
}
