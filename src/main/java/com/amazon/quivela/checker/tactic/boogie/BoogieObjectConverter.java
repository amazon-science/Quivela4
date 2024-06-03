/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.visitor.NewParamsCollector;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class BoogieObjectConverter extends AnalysisAdapter {

    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final BoogieMethods methods;
    private final BoogieClasses classes;
    private final BoogieConstants constants;
    private final String name;
    private final PrettyPrintStream out;
    private final String classId;

    private List<String> fields = new ArrayList<String>();
    private Map<String, List<String>> methodSigs = new HashMap();
    private Map<String, AMethodDef> methodDefs = new HashMap();
    private Map<String, Integer> invokedMethods = new HashMap();
    private Map<String, Checkpoint> checkpoints = new HashMap();

    public BoogieObjectConverter(SymbolTable symbolTable, BoogieFunctions functions, BoogieMethods methods, BoogieClasses classes, BoogieConstants constants, String name, PrettyPrintStream out, String classId) {
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.methods = methods;
        this.classes = classes;
        this.constants = constants;
        this.name = name;
        this.out = out;
        this.classId = classId;
    }

    @Override
    public void defaultCase(Node node)
    {
        throw new RuntimeException("Cannot convert " + node.getClass().getName());
    }

    public List<String> getFields() {
        return fields;
    }

    public Map<String, Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public Map<String, Integer> getInvokedMethods() {
        return invokedMethods;
    }

    public Map<String, List<String>> getMethodSigs() {
        return methodSigs;
    }

    public Map<String, AMethodDef> getMethodDefs() {
        return methodDefs;
    }

    @Override
    public void caseAMethodList(AMethodList methods) {
        for(Object methodObj : methods.getMethodDef()) {
            PMethodDef method = (PMethodDef)methodObj;
            method.apply(this);
        }
    }

    @Override
    public void caseANewExpr(ANewExpr node) {

        symbolTable.pushFrame(false);

        // convert methods first to produce method IDs used in class definition
        // method conversion needs fields for scope resolution
        NewParamsCollector col = new NewParamsCollector();
        node.getNewParamsList().apply(col);
        for(ANewParam curNewParam : col.getParams()) {

            String id = curNewParam.getIdentifier().getText().trim();
            symbolTable.addSymbol(id, Type.Opaque);
            fields.add(id);
        }

        node.getMethodList().apply(this);

        symbolTable.popFrame();

        out.print("procedure {:inline 1} " + name + ".new(");
        boolean first = true;
        for(ANewParam curNewParam : col.getParams()) {
            if (!first) {
                out.print(",");
            }
            String id = curNewParam.getIdentifier().getText().trim();
            out.print(id + ":T");
            first = false;

        }

        out.println(") returns (internal.result: Object) {");
        out.pushTab();

        // construct the object and add it to the heap associated with a fresh id.

        Map<String, String> temporaries = new HashMap();
        String objectId = newTempObject(temporaries);
        String objectMemId = newTempMemory(temporaries);

        out.println("var " + objectId + ":Object;");
        out.println("var " + objectMemId + ":Memory;");

        out.println(objectId + " := emptyObject;");
        out.println(objectMemId + " := toMemory(defaultValue);");

        for(ANewParam newParam : col.getParams()) {
            String curId = newParam.getIdentifier().getText().trim();
            out.println(objectMemId + "[internal.attribute.field." + curId + "] := " + curId + ";");
        }
        out.println(objectId + "[objectMemoryAttr] := " + objectMemId + ";");
        out.println(objectId + "[objectClassIdAttr] := " + classId + ";");

        out.println("internal.result := " + objectId + ";");

        out.popTab();
        out.println("}");
        out.println();

    }

    public Map<String, String> getScopedVars(String mapName) {
        return BoogieUtil.getScopedVars(fields, mapName);
    }

    private String newTemporary(Map<String, String> temporaries, String type) {
        for (int i = 0; ; i++) {
            String tmpName = "internal.tmp"+i;
            if (!temporaries.containsKey(tmpName)) {
                temporaries.put(tmpName, type);
                return tmpName;
            }
        }
    }
    private String newTemporary(Map<String, String> temporaries) {
        return newTemporary(temporaries,"T");
    }
    private String newTempMemory(Map<String, String> temporaries) {
        return newTemporary(temporaries, "Memory");
    }
    private String newTempObject(Map<String, String> temporaries) {
        return newTemporary(temporaries,"Object");
    }

    @Override
    public void caseAMethodDef(AMethodDef node) {

        symbolTable.pushFrame(false);

        methodDefs.put(node.getIdentifier().toString().trim(), node);

        String methodName = node.getIdentifier().getText().trim();
        String methodDef = node.toString().trim();

        constants.addMethodName(methodName, out);
        constants.addMethod(methodDef, out);

        String methodProcName = name + "." + methodName ;
        ByteArrayOutputStream baosParams = new ByteArrayOutputStream();
        PrettyPrintStream paramsOut = new PrettyPrintStream(baosParams);
        BoogieFormalParamsConverter paramsConverter = new BoogieFormalParamsConverter(paramsOut, Type.Opaque, BoogieFormalParamsConverter.Mode.NAME_AND_TYPE);
        node.getFormalParamsList().apply(paramsConverter);
        methodSigs.put(methodName, paramsConverter.getParamNames());
        symbolTable.addAll(paramsConverter.getParamNames(), Type.Opaque);

        out.print("procedure {:inline 1} " + methodProcName + "(internal.classId : ClassId, internal.objectId : ObjectId");
        if (!paramsConverter.getParamNames().isEmpty()) {
            out.print(",");
        }

        out.print(baosParams.toString());

        out.println(") returns (internal.result : T)");
        out.println("modifies objectMemory;");
        out.println("modifies functionState;");
        out.println("modifies heap; ");
        out.println("modifies checkpoints;");
        out.println("ensures (forall id:ObjectId :: objectValid(old(heap), id) ==> (objectValid(heap, id) && invokeSplit(heap, id)[targetObject][objectClassIdAttr] == invokeSplit(old(heap), id)[targetObject][objectClassIdAttr]));");
        out.println("{");
        out.pushTab();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream exprOut = new PrettyPrintStream(baos);
        exprOut.pushTab();

        Map<String, String> temporaries = new HashMap();
        // temporaries map is shared, so this temp will appear in the list of declared variables below

        Map<String, String> varMap = getScopedVars("objectMemory");
        for(String curParam : paramsConverter.getParamNames()) {
            varMap.put(curParam, curParam);
        }
        BoogieExprConverter converter = new BoogieExprConverter(symbolTable, functions, methods, classes, varMap, temporaries, exprOut);
        node.getFuncBody().apply(converter);
        invokedMethods.putAll(converter.getInvokedMethods());
        checkpoints.putAll(converter.getCheckpoints());
        for(String curVar : converter.getDeclaredVars().keySet()) {
            String type = converter.getDeclaredVars().get(curVar);
            out.println("var " + curVar + ":" + type + ";");
        }
        out.println();
        for(String curVar : converter.getDeclaredVars().keySet()) {
            String type = converter.getDeclaredVars().get(curVar);
            if (type.equals("T")) {
                out.println(curVar + " := defaultValue;");
            }
        }

        out.println(baos.toString());

        out.println("internal.result := " + converter.getValue(BoogieType.Opaque) + ";");
        out.println();

        out.popTab();
        out.println("}");
        out.println();

        symbolTable.popFrame();

        /*
        if (addDispatchMethods) {

            // declare an abstract variant of the method, if one doesn't already exist
            String boogieProcName = methods.getBoogieProcName(methodName);
            if (boogieProcName == null) {
                boogieProcName = methods.freshMethodProcName(methodName);
                FormalParamsCollector col = new FormalParamsCollector();
                node.getFormalParamsList().apply(col);

                BoogieUtil.declareTargetMethod(boogieProcName, col.getIds().size(), out);
                methods.put(methodName, boogieProcName);
            }

            // add a dispatch procedure that will invoke the method above when the class id is correct
            String dispatchProcName = methodProcName + ".dispatch";
            out.print("procedure {:inline 1} " + dispatchProcName + "(internal.classId : ClassId");
            if (!paramsConverter.getParamNames().isEmpty()) {
                out.print(",");
            }
            out.print(baosParams.toString());
            out.println(") returns (internal.result : T)");
            out.println("modifies objectMemory;");
            out.println("modifies heap;");
            out.println("ensures (forall id:ObjectId :: objectValid(old(heap), id) ==> (objectValid(heap, id) && invokeSplit(heap, id)[targetObject][objectClassIdAttr] == invokeSplit(old(heap), id)[targetObject][objectClassIdAttr]));");
            out.println("{");
            out.pushTab();

            out.println("if (internal.classId == " + name + ") {");
            out.pushTab();
            out.print("call internal.result := " + methodProcName + "(internal.classId");
            for (String curParam : paramsConverter.getParamNames()) {
                out.print(", " + curParam);
            }
            out.println(");");
            out.popTab();
            out.println("} else {");
            out.pushTab();
            String existingMethodProc = methods.getBoogieProcName(methodName);
            out.print("call internal.result := " + existingMethodProc + "(internal.classId");
            for (String curParam : paramsConverter.getParamNames()) {
                out.print(", " + curParam);
            }
            out.println(");");
            out.popTab();
            out.println("}");

            out.popTab();
            out.println("}");

            methods.put(methodName, dispatchProcName);
        }
        */
    }

}