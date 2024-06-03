/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic;

import com.amazon.quivela.checker.execution.ProofTaskConsumer;
import com.amazon.quivela.checker.visitor.BisimPropScopesCollector;
import com.amazon.quivela.checker.visitor.NewParamsCollector;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.node.*;
import com.amazon.quivela.checker.tactic.boogie.*;

import java.io.*;
import java.util.*;

/*
 The Bisim tactic checks the equivalence of two objects by bisimulation. That is, given a relational invariant that
 holds on the pair of objects, show that all method calls with equal inputs preserve this invariant and return equal
 outputs.
 */
public class Bisim {

    private static class BoogieBisimProp {
        private String prop;
        private BisimPropScopesCollector scopes;

        public BoogieBisimProp(String prop, BisimPropScopesCollector scopes) {
            this.prop = prop;
            this.scopes = scopes;
        }

        public String getProp() {
            return prop;
        }

        public BisimPropScopesCollector getScopes() {
            return scopes;
        }
    }

    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final Collection<AAxiomDecl> axioms;
    private final Map<String, ANewExpr> identifiedClasses;
    private final ProofTaskConsumer taskConsumer;

    public Bisim(ProofTaskConsumer taskConsumer, SymbolTable symbolTable, BoogieFunctions functions, Collection<AAxiomDecl> axioms, Map<String, ANewExpr> classes) {
        this.taskConsumer = taskConsumer;
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.axioms = axioms;
        this.identifiedClasses = classes;
    }

    private String toBoogieProp(PProp prop, BoogieConstants constants, PrettyPrintStream out, BoogieObjectConverter leftObj, BoogieObjectConverter rightObj, String leftHeap, String rightHeap, String leftObjectMem, String rightObjectMem) {
        symbolTable.pushFrame(true);
        for(String s : leftObj.getFields()) {
            symbolTable.addSymbol("left." + s, Type.Opaque);
        }
        for(String s : rightObj.getFields()) {
            symbolTable.addSymbol("right." + s, Type.Opaque);
        }

        BoogiePropConverter propConverter = new BoogiePropConverter(symbolTable, constants, identifiedClasses, functions, new HashMap(), out, leftHeap, rightHeap, leftObjectMem, rightObjectMem, leftObj.getFields(), rightObj.getFields());
        prop.apply(propConverter);

        symbolTable.popFrame();

        return propConverter.getValueString();
    }

    private List<BoogieBisimProp> toBoogieProps(List<ABisimProp> props, BoogieConstants constants, BoogieObjectConverter leftObj, BoogieObjectConverter rightObj, PrettyPrintStream out) {

        symbolTable.pushFrame(true);
        for(String s : leftObj.getFields()) {
            symbolTable.addSymbol("left." + s, Type.Opaque);
        }
        for(String s : rightObj.getFields()) {
            symbolTable.addSymbol("right." + s, Type.Opaque);
        }

        // If no invariant provided, create an invariant that states that heaps are equal
        // and fields at the same position are equal
        List<BoogieBisimProp> boogieProps = new ArrayList();
        if (props == null) {
            String boogieProp = "heap1==heap2";
            for (int i = 0; i < leftObj.getFields().size(); i++) {
                if (rightObj.getFields().size() > i) {
                    String leftMember = "objectMemory1[internal.attribute.field." + leftObj.getFields().get(i) + "]";
                    String rightMember = "objectMemory2[internal.attribute.field." + rightObj.getFields().get(i) + "]";
                    boogieProp = boogieProp + " && " + leftMember + "==" + rightMember;
                }
            }
            BisimPropScopesCollector scopesCol = new BisimPropScopesCollector();
            scopesCol.setInvariant(true);
            boogieProps.add(new BoogieBisimProp(boogieProp, scopesCol));
        } else {
            for(ABisimProp curProp : props) {
                BoogiePropConverter propConverter = new BoogiePropConverter(symbolTable, constants, identifiedClasses, functions, new HashMap(), out, "heap1", "heap2", "objectMemory1", "objectMemory2", leftObj.getFields(), rightObj.getFields());
                curProp.getProp().apply(propConverter);

                BisimPropScopesCollector scopesCol = new BisimPropScopesCollector();
                if(curProp.getBisimPropScopeClause() != null) {
                    curProp.getBisimPropScopeClause().apply(scopesCol);
                } else {
                    scopesCol.setInvariant(true);
                }
                boogieProps.add(new BoogieBisimProp(propConverter.getValueString(), scopesCol));
            }

        }
        symbolTable.popFrame();

        return boogieProps;

    }

    // Check that leftNew is equivalent to rightNew using the supplied invariants.
    public void check(File file, int line, int pos, PNewExpr leftNew, PNewExpr rightNew, List<ABisimProp> invariants) throws CheckException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream out = new PrettyPrintStream(baos);
        BoogieClasses classes = new BoogieClasses();
        BoogieMethods methods = new BoogieMethods();
        BoogieConstants constants = new BoogieConstants();

        BoogieUtil.writePrelude(out);
        BoogieUtil.writeSymbols(symbolTable, out);
        BoogieUtil.writeFuncDecls(symbolTable, functions, methods, classes, constants, out);
        BoogieUtil.writeAxioms(symbolTable, axioms, constants, identifiedClasses, functions, out);


        BoogieClassDecls classDecls = new BoogieClassDecls(symbolTable, functions, methods, classes, constants, out);
        leftNew.apply(classDecls);
        String leftClassId = classes.getByDef(leftNew);
        ByteArrayOutputStream objBaos = new ByteArrayOutputStream();
        PrettyPrintStream objOut = new PrettyPrintStream(objBaos);
        BoogieObjectConverter leftObj = new BoogieObjectConverter(symbolTable, functions, methods, classes, constants, "left", objOut, leftClassId);
        leftNew.apply(leftObj);

        constants.addFields(leftObj.getFields(), out);

        classDecls = new BoogieClassDecls(symbolTable, functions, methods, classes, constants, out);
        rightNew.apply(classDecls);
        String rightClassId = classes.getByDef(rightNew);

        objBaos = new ByteArrayOutputStream();
        objOut = new PrettyPrintStream(objBaos);
        BoogieObjectConverter rightObj = new BoogieObjectConverter(symbolTable, functions, methods, classes, constants, "right", objOut, rightClassId);
        rightNew.apply(rightObj);

        constants.addFields(rightObj.getFields(), out);

        out.println();

        // write both.new
        List<BoogieBisimProp> boogieInvariants = toBoogieProps(invariants, constants, leftObj, rightObj, out);

        out.println("procedure both.new(internal.objectId : ObjectId) returns (functionState1 : FunctionState, functionState2 : FunctionState)");

        for (BoogieBisimProp curProp : boogieInvariants) {
            if (curProp.getScopes().isInvariant()) {
                out.println("ensures (" + curProp.getProp() + ");");
            }
        }

        out.println("ensures functionState1==functionState1;");
        out.println("modifies objectMemory;");
        out.println("modifies objectMemory1;");
        out.println("modifies objectMemory2;");
        out.println("modifies functionState;");
        out.println("modifies heap1;");
        out.println("modifies heap2;");
        out.println("modifies heap;");
        out.println("modifies checkpoints;");
        out.println("modifies checkpoints1;");
        out.println("modifies checkpoints2;");
        out.println("{");

        out.pushTab();

        out.println("var initFunctionState : FunctionState;");
        out.println("var initHeap : Heap;");

        // convert new parameters
        ByteArrayOutputStream leftNewParamsBaos = new ByteArrayOutputStream();
        PrettyPrintStream leftNewParamsOut = new PrettyPrintStream(leftNewParamsBaos);

        Map<String, String> temporaries = new HashMap();
        Map<String, Checkpoint> checkpoints = new HashMap();

        NewParamsCollector leftCol = new NewParamsCollector();
        List<String> leftParams = new ArrayList();
        leftNew.apply(leftCol);
        for(ANewParam curParam : leftCol.getParams()) {
            BoogieExprConverter conv = new BoogieExprConverter(symbolTable, functions, methods, classes, new HashMap(), temporaries, leftNewParamsOut);
            curParam.getExpr().apply(conv);
            temporaries.putAll(conv.getDeclaredVars());
            leftParams.add(conv.getValue());
            checkpoints.putAll(conv.getCheckpoints());
            String curId = curParam.getIdentifier().getText().trim();
            leftNewParamsOut.println("objectMemory[internal.attribute.field." + curId + "] := " + conv.getValue() + ";");
        }
        ByteArrayOutputStream rightNewParamsBaos = new ByteArrayOutputStream();
        PrettyPrintStream rightNewParamsOut = new PrettyPrintStream(rightNewParamsBaos);
        NewParamsCollector rightCol = new NewParamsCollector();
        List<String> rightParams = new ArrayList();
        rightNew.apply(rightCol);
        for(ANewParam curParam : rightCol.getParams()) {
            BoogieExprConverter conv = new BoogieExprConverter(symbolTable, functions, methods, classes, new HashMap(), temporaries, rightNewParamsOut);
            curParam.getExpr().apply(conv);
            temporaries.putAll(conv.getDeclaredVars());
            checkpoints.putAll(conv.getCheckpoints());
            rightParams.add(conv.getValue());
            String curId = curParam.getIdentifier().getText().trim();
            rightNewParamsOut.println("objectMemory[internal.attribute.field." + curId + "] := " + conv.getValue() + ";");
        }


        // declare temporaries and locals
        for(String curVar : temporaries.keySet()) {
            String type = temporaries.get(curVar);
            out.println("var " + curVar + ":" + type + ";");
        }

        for(String curVar : temporaries.keySet()) {
            String type = temporaries.get(curVar);
            if (type.equals("T")) {
                out.println(curVar + " := defaultValue;");
            }
        }

        out.println("initFunctionState := functionState;");
        out.println("initHeap := heap;");

        out.println("// left new");
        out.println("checkpoints := initCheckpoints;");
        out.println("objectMemory := toMemory(defaultValue);");
        out.println(leftNewParamsBaos.toString());

        out.println("checkpoints1 := checkpoints;");
        out.println("objectMemory1 := objectMemory;");
        out.println("functionState1 := functionState;");
        out.println("heap1 := heap;");

        out.println("functionState := initFunctionState;");
        out.println("heap := initHeap;");
        out.println("// right new");
        out.println("checkpoints := initCheckpoints;");
        out.println("objectMemory := toMemory(defaultValue);");
        out.println(rightNewParamsBaos.toString());

        out.println("checkpoints2 := checkpoints;");
        out.println("objectMemory2 := objectMemory;");
        out.println("functionState2 := functionState;");
        out.println("heap2 := heap;");

        // TODO: duplicate code below
        if (invariants != null) {
            for (ABisimProp curProp : invariants) {
                BisimPropScopesCollector scopesCol = new BisimPropScopesCollector();
                if (curProp.getBisimPropScopeClause() != null) {
                    curProp.getBisimPropScopeClause().apply(scopesCol);
                }
                for(BisimPropScopesCollector.Checkpoint curCheckpoint : scopesCol.getCheckpoints()) {

                    String leftId = BoogieUtil.toCheckpointId(curCheckpoint.getLeft());
                    String rightId = BoogieUtil.toCheckpointId(curCheckpoint.getRight());

                    if(checkpoints.get(curCheckpoint.getLeft())!=null && checkpoints.get(curCheckpoint.getRight())!=null) {

                        String checkpointProp = toBoogieProp(curProp.getProp(), constants, out, leftObj, rightObj,
                                "checkpoints1[" + leftId + "][checkpointHeap]",
                                "checkpoints2[" + rightId + "][checkpointHeap]",
                                "checkpoints1[" + leftId + "][checkpointMemory]",
                                "checkpoints2[" + rightId + "][checkpointMemory]");
                        out.println("assert (" + checkpointProp + ");");
                        out.println("assert (checkpoints1[" + leftId + "][checkpointFunctionState]==checkpoints2[" + rightId + "][checkpointFunctionState]);");
                    }
                }

            }
        }

        out.popTab();
        out.println("}");
        out.println();

        // create a "both" method from left methods
        // analysis fails if an object has a method that the other object does not
        for(String methodName : rightObj.getMethodSigs().keySet()) {
            if(! leftObj.getMethodSigs().containsKey(methodName)) {
                throw new CheckException(file, line, pos, "Objects in bisimulation must have identical method signatures.");
            }
        }

        for(String methodName : leftObj.getMethodSigs().keySet()) {

            temporaries = new HashMap();
            checkpoints = new HashMap();

            if(! rightObj.getMethodSigs().containsKey(methodName)) {
                throw new CheckException(file, line, pos, "Objects in bisimulation must have identical method signatures.");
            }

            List<String> leftMethodSig = leftObj.getMethodSigs().get(methodName);
            Map<String, String> leftArgMap = new HashMap<>();
            int argPos = 0;
            for(String curArg : leftMethodSig) {
                leftArgMap.put(curArg, "a" + argPos);
                argPos++;
            }

            List<String> rightMethodSig = rightObj.getMethodSigs().get(methodName);
            Map<String, String> rightArgMap = new HashMap<>();
            argPos = 0;
            for(String curArg : rightMethodSig) {
                rightArgMap.put(curArg, "a" + argPos);
                argPos++;
            }

            int numArgs = Math.max(leftMethodSig.size(), rightMethodSig.size());

            out.print("procedure both." + methodName + "(internal.objectId : ObjectId");
            for(int i = 0; i< numArgs; i++) {
                out.print(",a" + i + ":T");
            }
            out.println(") returns (internal.r1:T, internal.r2:T, functionState1:FunctionState, functionState2:FunctionState)");
            for (BoogieBisimProp curProp : boogieInvariants) {
                if (curProp.getScopes().isInvariant()) {
                    out.println("requires (" + curProp.getProp() + ");");
                }
            }

            out.println("modifies functionState;");
            out.println("modifies heap1;");
            out.println("modifies heap2;");
            out.println("modifies heap;");
            out.println("modifies objectMemory1;");
            out.println("modifies objectMemory2;");
            out.println("modifies objectMemory;");
            out.println("modifies checkpoints;");
            out.println("modifies checkpoints1;");
            out.println("modifies checkpoints2;");

            for (BoogieBisimProp curProp : boogieInvariants) {
                if (curProp.getScopes().isInvariant()) {
                    out.println("ensures (" + curProp.getProp() + ");");
                }
            }
            out.println("ensures functionState1==functionState2;");
            out.println("ensures internal.r1==internal.r2; {");
            out.pushTab();

            ByteArrayOutputStream leftBaos = new ByteArrayOutputStream();
            PrettyPrintStream leftExprOut = new PrettyPrintStream(leftBaos);

            Map leftVarMap = BoogieUtil.getScopedVars(leftObj.getFields(), "objectMemory");
            leftVarMap.putAll(leftArgMap);

            // add symbol table frames for fields, method parameters, and method body
            symbolTable.pushFrame(false);
            symbolTable.addAll(leftObj.getFields(), Type.Opaque);
            symbolTable.pushFrame(false);
            symbolTable.addAll(leftMethodSig, Type.Opaque);
            symbolTable.pushFrame(false);

            BoogieExprConverter leftExprConverter = new BoogieExprConverter(symbolTable, functions, methods, classes, leftVarMap, temporaries, leftExprOut);
            leftObj.getMethodDefs().get(methodName).getFuncBody().apply(leftExprConverter);

            symbolTable.popFrame();
            symbolTable.popFrame();
            symbolTable.popFrame();

            ByteArrayOutputStream rightBaos = new ByteArrayOutputStream();
            PrettyPrintStream rightExprOut = new PrettyPrintStream(rightBaos);
            Map rightVarMap = BoogieUtil.getScopedVars(rightObj.getFields(), "objectMemory");
            rightVarMap.putAll(leftArgMap);

            // add symbol table frames for fields, method parameters, and method body
            symbolTable.pushFrame(false);
            symbolTable.addAll(rightObj.getFields(), Type.Opaque);
            symbolTable.pushFrame(false);
            symbolTable.addAll(rightMethodSig, Type.Opaque);
            symbolTable.pushFrame(false);

            BoogieExprConverter rightExprConverter = new BoogieExprConverter(symbolTable, functions, methods, classes, rightVarMap, temporaries, rightExprOut);
            rightObj.getMethodDefs().get(methodName).getFuncBody().apply(rightExprConverter);

            symbolTable.popFrame();
            symbolTable.popFrame();
            symbolTable.popFrame();

            // declare all local variables
            Map<String, String> allDeclaredVars = new HashMap();
            allDeclaredVars.putAll(leftExprConverter.getDeclaredVars());
            allDeclaredVars.putAll(rightExprConverter.getDeclaredVars());

            for(String curVar : allDeclaredVars.keySet()) {
                String type = allDeclaredVars.get(curVar);
                out.println("var " + curVar + ":" + type + ";");
            }

            out.println("var initFunctionState : FunctionState;");
            out.println();


            out.println("initFunctionState := functionState;");
            out.println("heap := heap1;");
            out.println("objectMemory := objectMemory1;");
            out.println("checkpoints := initCheckpoints;");

            for(Checkpoint curCheckpoint : leftExprConverter.getCheckpoints().values()) {
                BoogieUtil.saveCheckpoint(out, curCheckpoint.getId(), "defaultValue");
            }

            out.println("// left method");

            for(String curVar : allDeclaredVars.keySet()) {
                String type = allDeclaredVars.get(curVar);
                if (type.equals("T")) {
                    out.println(curVar + " := defaultValue;");
                }
            }
            out.println();

            out.println(leftBaos.toString());
            out.println("internal.r1 := " + leftExprConverter.getValue(BoogieType.Opaque) + ";");

            out.println("functionState1 := functionState;");
            out.println("heap1 := heap;");
            out.println("objectMemory1 := objectMemory;");
            out.println("checkpoints1 := checkpoints;");

            out.println("functionState := initFunctionState;");
            out.println("heap := heap2;");
            out.println("objectMemory := objectMemory2;");
            out.println("checkpoints := initCheckpoints;");

            for(Checkpoint curCheckpoint : leftExprConverter.getCheckpoints().values()) {
                BoogieUtil.saveCheckpoint(out, curCheckpoint.getId(), "defaultValue");
            }

            out.println();
            out.println("// right method");

            for(String curVar : allDeclaredVars.keySet()) {
                String type = allDeclaredVars.get(curVar);
                if (type.equals("T")) {
                    out.println(curVar + " := defaultValue;");
                }
            }
            out.println();
            out.println(rightBaos.toString());
            out.println("internal.r2 := " + rightExprConverter.getValue(BoogieType.Opaque) + ";");

            out.println("functionState2 := functionState;");
            out.println("heap2 := heap;");
            out.println("objectMemory2 := objectMemory;");
            out.println("checkpoints2 := checkpoints;");

            // insert checkpoint assertions
            if (invariants != null) {
                for (ABisimProp curProp : invariants) {
                    BisimPropScopesCollector scopesCol = new BisimPropScopesCollector();
                    if (curProp.getBisimPropScopeClause() != null) {
                        curProp.getBisimPropScopeClause().apply(scopesCol);
                    }
                    for(BisimPropScopesCollector.Checkpoint curCheckpoint : scopesCol.getCheckpoints()) {

                        String leftId = BoogieUtil.toCheckpointId(curCheckpoint.getLeft());
                        String rightId = BoogieUtil.toCheckpointId(curCheckpoint.getRight());

                        if(leftExprConverter.getCheckpoints().get(curCheckpoint.getLeft()) != null && rightExprConverter.getCheckpoints().get(curCheckpoint.getRight()) != null) {

                            String checkpointProp = toBoogieProp(curProp.getProp(), constants, out, leftObj, rightObj,
                                    "checkpoints1[" + leftId + "][checkpointHeap]",
                                    "checkpoints2[" + rightId + "][checkpointHeap]",
                                    "checkpoints1[" + leftId + "][checkpointMemory]",
                                    "checkpoints2[" + rightId + "][checkpointMemory]");
                            out.println("assert (" + checkpointProp + ");");
                            out.println("assert (checkpoints1[" + leftId + "][checkpointFunctionState]==checkpoints2[" + rightId + "][checkpointFunctionState]);");
                        }
                    }

                }
            }

            out.popTab();
            out.println("}");
            out.println();
        }

        out.close();
        BoogieProofTask boogieTask = new BoogieProofTask(file, line, pos, baos.toString(), "Checking bisimulation", "Bisimulation check failed.", 2);
        taskConsumer.add(boogieTask);
    }

}
