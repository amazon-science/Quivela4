/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic;

import com.amazon.quivela.checker.*;
import com.amazon.quivela.checker.visitor.*;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.io.File;
import java.util.*;

/*
 The Inline tactic removes a new expression within an object by inlining the new object into the object
 that creates it. Currently only supports inlining objects that are created in the constructor of the
 outer object.
 */

public class Inline {

    private List<CheckException> exceptions = new ArrayList<CheckException>();
    private Stack<ANewParam> paramStack = new Stack<ANewParam>();

    // Inline objects created in the constructor of left
    public void transform(File file, PExpr left, SymbolTable symbolTable, List newIds) throws CheckException {
        Inliner inliner = new Inliner(file, symbolTable, newIds);
        left.apply(inliner);
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    class Inliner extends DepthFirstAdapter {

        private final File file;
        private SymbolTable symbolTable;
        List<String> newIds = new ArrayList();

        public Inliner(File file, SymbolTable symbolTable, List<String> newIds) {
            this.file=file;
            this.symbolTable = symbolTable;
            this.newIds.addAll(newIds);

        }

        @Override
        public void inANewParam(ANewParam node) {
            paramStack.push(node);
        }

        @Override
        public void outANewParam(ANewParam node) {
            paramStack.pop();
        }

        @Override
        public void outANewExpr(ANewExpr node) {
            if(!paramStack.empty()) {
                doInline(paramStack.peek(), node);
            }
        }

        public void doInline(ANewParam newParam, ANewExpr newExpr) {
            NewParamsCollector collector = new NewParamsCollector();
            if (newExpr.getNewParamsList() != null) {
                newExpr.getNewParamsList().apply(collector);
            }
            List<ANewParam> innerParams = collector.getParams();

            ANewParams outerParamsNode = getNewParams(newParam);
            collector = new NewParamsCollector();
            outerParamsNode.apply(collector);
            List<ANewParam> outerParams = collector.getParams();

            Map<String, String> memberIdMap = new HashMap<>();
            Set<String> outerMembers = new HashSet<>();
            for(ANewParam curParam : outerParams) {
                String curId = curParam.getIdentifier().getText();
                if (!curId.equals(newParam.getIdentifier().getText())) {
                    outerMembers.add(curId);
                }
            }

            symbolTable.pushFrame(false);
            LocalVariableDeclCollector localDeclCollector = new LocalVariableDeclCollector(symbolTable);
            newExpr.getMethodList().apply(localDeclCollector);
            SymbolTableFrame newFrame = symbolTable.popFrame();

            // remove the inlined assignment
            String assignedId = newParam.getIdentifier().getText();
            int inlinePos = 0;
            for(; inlinePos < outerParams.size(); inlinePos++) {
                ANewParam curParam = outerParams.get(inlinePos);
                if(curParam.getIdentifier().getText().equals(assignedId)) {
                    outerParams.remove(inlinePos);
                    break;
                }
            }

            // create fresh outer member variables for each inner member variable
            // use supplied IDs, if possible
            for(ANewParam curParam : innerParams) {

                Set<String> allVars = new HashSet();
                allVars.addAll(outerMembers);
                allVars.addAll(newFrame.getIds());

                String innerId = curParam.getIdentifier().getText();
                String freshId = createFreshId("m_", allVars, newIds);
                memberIdMap.put(innerId, freshId);
                outerMembers.add(freshId);

                ANewParam newOuterParam = (ANewParam)curParam.clone();
                newOuterParam.getIdentifier().replaceBy(new TIdentifier(freshId));
                outerParams.add(inlinePos++, newOuterParam);
            }

            MethodsCollector methodsCollector = new MethodsCollector();
            newExpr.getMethodList().apply(methodsCollector);
            Map<String, AMethodDef> methods = methodsCollector.getMethods();

            Optional<ANewExpr> outerNewOpt = Util.getParent(newParam, ANewExpr.class);
            if (outerNewOpt.isPresent()) {
                ANewExpr outerNew = outerNewOpt.get();
                MethodInliner methodInliner = new MethodInliner(file, assignedId, methods, outerMembers, memberIdMap, symbolTable);
                outerNew.getMethodList().apply(methodInliner);
                ((ANewParamsList) outerNew.getNewParamsList()).getNewParams().replaceBy(toNewParams(outerParams));
            } else {
                throw new RuntimeException("Inlining at multiple locations is not implemented. Use unfold(<id>) to control which locations are available for inlining.");
            }
        }

        private ANewParams toNewParams(List<ANewParam> ls) {
            return Util.toNewParams(ls);
        }

        private ANewParams getNewParams(Node n) {
            while(true) {
                if (n instanceof ANewParams) {
                    return (ANewParams)n;
                } else {
                    n = n.parent();
                }
            }
        }
    }

    private static String createFreshId(String prefix, Collection<String> ids) {

        return createFreshId(prefix, ids, new ArrayList());
    }

    private static String createFreshId(String prefix, Collection<String> ids, List<String> newIds) {

        if (!newIds.isEmpty()) {
            String curId = newIds.remove(0);
            // TODO: warning if ID cannot be used
            if (!ids.contains(curId)) {
                return curId;
            }
        }

        for(int i = 0; true; ++i) {
            String curId = prefix + i;
            if (!ids.contains(curId)) {
                return curId;
            }
        }
    }

    private class MethodInliner extends DepthFirstAdapter {

        private int disableStack = 0;

        private final File file;
        private String inlineId;
        private Map<String, AMethodDef> innerMethods;
        private Set<String> outerMembers;
        private Map<String, String> memberIdMap;
        private SymbolTable symbolTable;

        private Set<String> outerLocals = null;

        public MethodInliner(File file, String inlineId, Map<String, AMethodDef> innerMethods, Set<String> outerMembers, Map<String, String> memberIdMap, SymbolTable symbolTable) {
            this.file=file;
            this.inlineId = inlineId;
            this.innerMethods = innerMethods;
            this.outerMembers = outerMembers;
            this.memberIdMap = memberIdMap;
            this.symbolTable = symbolTable;
        }

        @Override
        public void inANewExpr(ANewExpr node) {
            node.getNewParamsList().apply(this);
            ++disableStack;
        }

        @Override
        public void outANewExpr(ANewExpr node) {
           --disableStack;
        }

        @Override
        public void inAMethodDef(AMethodDef node) {
            if (disableStack != 0) {
                return;
            }

            FormalParamsCollector paramsCollector = new FormalParamsCollector();
            node.apply(paramsCollector);
            List<String> methodLocalIds = paramsCollector.getIds();

            VariableReferenceCollector refCollector = new VariableReferenceCollector();
            if (node.getFuncBody() != null) {
                node.getFuncBody().apply(refCollector);
            }
            Set<String> refs = refCollector.getIds();

            refs.addAll(methodLocalIds);
            this.outerLocals = refs;

        }

        @Override
        public void outAInvokeExpr(AInvokeExpr node) {
            if (disableStack != 0) {
                return;
            }

            EqualsLookup eqs = new EqualsLookup(inlineId);
            node.getPrimaryExpr().apply(eqs);
            if(!eqs.isEqual()) {
                return;
            }

            AMethodDef targetMethod = innerMethods.get(node.getIdentifier().getText());
            if (targetMethod == null) {
                throw new RuntimeException(
                        new CheckException(file, node.getIdentifier(),
                                "method of invocation target class does not exist: " + node.getIdentifier().getText()));
            }

            FormalParamsCollector paramsCollector = new FormalParamsCollector();
            targetMethod.getFormalParamsList().apply(paramsCollector);
            List<String> innerParams = paramsCollector.getIds();

            VariableReferenceCollector refCollector = new VariableReferenceCollector();
            if (targetMethod.getFuncBody() != null) {
                targetMethod.getFuncBody().apply(refCollector);
            }
            Set<String> innerLocals = refCollector.getIds();
            innerLocals.removeAll(memberIdMap.keySet());
            innerLocals.removeAll(symbolTable.allSymbols());
            innerLocals.addAll(innerParams);

            Set<String> allOuter = new HashSet<>();
            allOuter.addAll(innerLocals);
            allOuter.addAll(outerLocals);
            allOuter.addAll(outerMembers);

            Map<String, String> localIdMap = new HashMap<>();
            for(String curInner : innerLocals) {
                String newOuter = createFreshId("l_", allOuter);
                localIdMap.put(curInner, newOuter);
                allOuter.add(newOuter);
            }

            // assignment to fresh for each actual parameter
            List<PAssignExpr> exprSeq = new ArrayList<>();
            ActualParamsCollector actualCollector = new ActualParamsCollector();
            node.getActualParams().apply(actualCollector);
            List<PExpr> actualParams = actualCollector.getExprs();
            for(int i = 0; i < innerParams.size(); i++) {
                String curId = innerParams.get(i);
                curId = localIdMap.get(curId);
                PPrimaryExpr assignValue = Util.defaultPrimaryExpr();
                if(actualParams.size() >= i) {
                    assignValue = Util.toPrimaryExpr(actualParams.get(i));
                }
                exprSeq.add(Util.assignExpr(curId, assignValue));
            }

            // add member id map to local id map so members will be substituted as well
            for(Map.Entry<String, String> entry : memberIdMap.entrySet()) {
                localIdMap.put(entry.getKey(), entry.getValue());
            }

            if (targetMethod.getFuncBody() != null) {
                PExpr bodyExpr = ((AFuncBody)targetMethod.getFuncBody()).getExpr();

                PExpr methodBody = (PExpr)bodyExpr.clone();
                VarSubstituter sub = new VarSubstituter(localIdMap);
                methodBody.apply(sub);
                exprSeq.add(Util.toAssignExpr(Util.toPrimaryExpr(methodBody)));

                node.parent().replaceBy(Util.toPrimaryExpr(Util.toSequence(exprSeq)));
            }


        }
    }

    class EqualsLookup extends AnalysisAdapter {

        private String id;
        private boolean equal = false;

        public EqualsLookup(String id) {
            this.id = id;
        }

        @Override
        public void caseALookupPrimaryExpr(ALookupPrimaryExpr node) {
            equal = node.getIdentifier().getText().equals(id);
        }

        public boolean isEqual() {
            return equal;
        }
    }
}
