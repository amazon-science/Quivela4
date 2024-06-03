/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.visitor.ActualParamsCollector;
import com.amazon.quivela.checker.visitor.IdentifierCollector;
import com.amazon.quivela.checker.visitor.NewParamsCollector;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.*;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class BoogieExprConverter extends DepthFirstAdapter {

    private final Map<String, String> varMap;
    private PrettyPrintStream out;

    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final BoogieMethods methods;
    private final BoogieClasses classes;

    private Deque<Value> value = new ArrayDeque();
    private Map<String, String> temporaries;
    private Set<String> letDecls = new HashSet<String>();
    private Map<String, Integer> invokedMethods = new HashMap();
    private Map<String, Checkpoint> checkpoints = new HashMap();
    private String lastSplitHeapId = null;

    public BoogieExprConverter(SymbolTable symbolTable, BoogieFunctions functions, BoogieMethods methods, BoogieClasses classes, Map<String, String> varMap, Map<String, String> temporaries, PrettyPrintStream out) {
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.methods = methods;
        this.classes = classes;
        this.temporaries = temporaries;
        this.varMap = varMap;
        this.out = out;
    }

    public Map<String, Integer> getInvokedMethods() {
        return invokedMethods;
    }

    public Map<String, String> getDeclaredVars() {
        Map<String, String> result = new HashMap();
        result.putAll(temporaries);
        for(String curName : letDecls) {
            result.put(curName, "T");
        }
        return result;
    }

    public Map<String, Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public String getValue() {

        if (value.size() > 1) {
            throw new RuntimeException("Values lost during conversion");
        }

        return value.peek().toOpaqueString();
    }

    public String getValue(BoogieType type) {
        if (value.size() > 1) {
            throw new RuntimeException("Values lost during conversion");
        }

        return value.peek().getValue(type);
    }

    private String newTemporary(String type) {
        for (int i = 0; ; i++) {
            String tmpName = "internal.tmp"+i;
            if (!temporaries.containsKey(tmpName)) {
                temporaries.put(tmpName, type);
                return tmpName;
            }
        }
    }
    private String newTemporary(BoogieType type) {
        return newTemporary(type.getBoogieString());
    }

    /*
    private String newTemporary() {
        return newTemporary("T");
    }
    */

    private String newTempMemory() {
        return newTemporary("Memory");
    }

    public static String getScopedVar(String scope, String ident) {
        if (scope == null) {
            return ident;
        } else {
            return scope + "[internal.attribute.field." + ident + "]";
        }
    }

    private String getLookupString(String ident) {
        String mapped = varMap.get(ident);
        return mapped == null ? ident : mapped;
    }

    @Override
    public void caseADotdotdotPrimaryExpr(ADotdotdotPrimaryExpr node) {
        throw new RuntimeException("Internal error: ... not rewritten to previous expression.");
    }

    @Override
    public void inANumericLiteral(ANumericLiteral node) {
        value.push(new Value(BoogieType.Integer, node.getNumericLiteral().getText().trim()));
    }

    @Override
    public void inABoolLiteral(ABoolLiteral node) {
        value.push(new Value(BoogieType.Boolean, node.getBoolLiteral().getText().trim()));
    }

    @Override
    public void inALookupPrimaryExpr(ALookupPrimaryExpr node) {

        String id = node.getIdentifier().getText().trim();
        Type type = symbolTable.getType(id);
        if (type == null) {
            // The analysis doesn't keep track of all variables/types, but it would help find bugs if it did
            // Everything that is not tracked is opaque
            type = Type.Opaque;
        }
        value.push(new Value(BoogieUtil.toBoogieType(type), getLookupString(id)));
    }

    @Override
    public void outARelOpBoolExpr(ARelOpBoolExpr node) {
        String op = node.getRelOp().getText().trim();
        Value right = value.pop();
        Value left = value.pop();

        String opStr = node.getRelOp().toString().trim();
        BoogieType inType = BoogieUtil.getInType(opStr, left.getType(), right.getType());
        value.push(new Value(BoogieType.Boolean, "(" + left.getValue(inType) + ")" + BoogieUtil.convertBoolOp(opStr) + "(" + right.getValue(inType) + ")"));

    }

    @Override
    public void outATobitsPrimaryExpr(ATobitsPrimaryExpr node) {
        value.push(new Value(BoogieType.Bitstring, value.pop().toBitstringString()));
    }

    @Override
    public void caseAAssertPrimaryExpr(AAssertPrimaryExpr node) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream propOut = new PrettyPrintStream(baos);
        Map<String, ANewExpr> classes = new HashMap();

        BoogieConstants constants = new BoogieConstants();
        symbolTable.pushLogicalFrame();
        BoogiePropConverter propConverter = new BoogiePropConverter(symbolTable, constants, classes, functions, varMap, propOut);
        node.getProp().apply(propConverter);
        symbolTable.popFrame();
        out.println("assert (" + propConverter.getValueString() + ");");

        value.push(new Value(BoogieType.Bitstring, "nil"));
    }

    @Override
    public void caseAAdmitPrimaryExpr(AAdmitPrimaryExpr node) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream propOut = new PrettyPrintStream(baos);
        Map<String, ANewExpr> classes = new HashMap();

        BoogieConstants constants = new BoogieConstants();
        symbolTable.pushLogicalFrame();
        BoogiePropConverter propConverter = new BoogiePropConverter(symbolTable, constants, classes, functions, varMap, propOut);
        node.getProp().apply(propConverter);
        symbolTable.popFrame();
        out.println("assume (" + propConverter.getValueString() + ");");

        value.push(new Value(BoogieType.Bitstring, "nil"));
    }

    @Override
    public void outAMapLookupPrimaryExpr(AMapLookupPrimaryExpr node) {
        Value index = value.pop();
        Value map = value.pop();

        value.push(new Value(BoogieType.Opaque, "toMemory(" + map.getValue() + ")[" + index.toBitstringString() + "]"));
    }

    @Override
    public void outAMapUpdatePrimaryExpr(AMapUpdatePrimaryExpr node) {
        Value updateValue = value.pop();
        Value index = value.pop();
        Value map = value.pop();

        value.push(new Value(BoogieType.Memory, "toMemory(" + map.getValue() + ")[" + index.toBitstringString() + " := " + updateValue.toOpaqueString() + "]"));
    }

    @Override
    public void caseALogicOpLogicExpr(ALogicOpLogicExpr node) {
        node.getLeft().apply(this);
        Value leftValue = value.pop();

        out.println();
        String op = node.getLogicOp().getText().trim();
        String test;
        if (op.equals("&")) {
            test = leftValue.toBooleanString();
        } else if (op.equals("|")) {
            test = "!(" + leftValue.toBooleanString() + ")";
        } else {
            throw new RuntimeException("op not supported: " + op);
        }

        // TODO: We can avoid conversion and when both left and right have more specific and compatible types
        String tmp = getLookupString(newTemporary(BoogieType.Opaque));
        out.println(tmp + " := " + leftValue.toOpaqueString() + ";");
        out.println("if (" + test + ") {");
        out.pushTab();
        node.getRight().apply(this);
        Value rightValue = value.pop();
        out.println(tmp + " := " + rightValue.toOpaqueString() + ";");
        out.popTab();
        out.println("}");

        value.push(new Value(BoogieType.Opaque, tmp));

    }

    @Override
    public void outAProductProductExpr(AProductProductExpr node) {
        Value right = value.pop();
        Value left = value.pop();

        String boogieSumOp = toBoogieOp(node.getProductOp());

        value.push(new Value(BoogieType.Integer, "(" + left.toIntegerString() + boogieSumOp + right.toIntegerString() + ")"));
    }

    @Override
    public void outASumSumExpr(ASumSumExpr node) {
        Value right = value.pop();
        Value left = value.pop();

        String boogieSumOp = toBoogieOp(node.getSumOp());

        value.push(new Value(BoogieType.Integer, "(" + left.toIntegerString() + boogieSumOp + right.toIntegerString() + ")"));
    }


    private static String toBoogieOp(TSumOp op) {
        if (op.getText().equals("+")) {
            return "+";
        } else {
            throw new RuntimeException("Unsupported operation: " + op.getText());
        }
    }

    private static String toBoogieOp(TProductOp op) {
        if (op.getText().equals("%")) {
            return " mod ";
        } else {
            throw new RuntimeException("Unsupported operation: " + op.getText());
        }
    }

    @Override
    public void outASequenceLogicExpr(ASequenceLogicExpr node) {
        Value right = value.pop();
        Value left = value.pop();
        value.push(right);
    }

    @Override
    public void caseATernaryOpLogicExpr(ATernaryOpLogicExpr node) {

        node.getTest().apply(this);
        Value testValue = value.pop();

        // TODO: Avoid conversion in cases where it is not necessary
        String temp = newTemporary(BoogieType.Opaque);
        out.println("if (" + testValue.toBooleanString() + ") {");
        out.pushTab();
        node.getIfso().apply(this);
        Value soValue = value.pop();
        out.println(temp + " := " + soValue.toOpaqueString() + ";");
        out.popTab();
        out.println("} else {");
        out.pushTab();
        node.getIfnot().apply(this);
        Value notValue = value.pop();
        out.println(temp + " := " + notValue.toOpaqueString() + ";");
        out.popTab();
        out.println("}");

        value.push(new Value(BoogieType.Opaque, temp));
    }

    private void pushProcFuncExpr(String funcName, Collection<Value> actualParams, Type returnType) {

        AFuncDecl decl = functions.getByName(funcName);
        List<Type> parameterTypes = Util.functionParameterTypes(decl);

        String tmp = newTemporary(BoogieUtil.toBoogieType(returnType));
        int i = 0;
        out.print("call " + getLookupString(tmp) + ":=" + funcName + "(internal.objectId");
        for(Value curParam : actualParams) {
            out.print("," + curParam.getValue(BoogieUtil.toBoogieType(parameterTypes.get(i))));
            i++;
        }
        out.println(");");

        value.push(new Value(BoogieUtil.toBoogieType(returnType), getLookupString(tmp)));
    }

    private void pushPureFuncExpr(String funcName, Collection<Value> actualParams, Type returnType) {

        AFuncDecl decl = functions.getByName(funcName);
        List<Type> parameterTypes = Util.functionParameterTypes(decl);

        StringBuffer newVal = new StringBuffer(funcName + "(");
        int i = 0;
        for(Value curParam : actualParams) {
            if (i>0) {
                newVal.append(",");
            }
            newVal.append(curParam.getValue(BoogieUtil.toBoogieType(parameterTypes.get(i))));
            i++;
        }

        newVal.append(")");
        value.push(new Value(BoogieUtil.toBoogieType(returnType), newVal.toString()));
    }


    @Override
    public void outAFuncExpr(AFuncExpr node) {
        String funcName = node.getIdentifier().getText().trim();
        ActualParamsCollector paramsCollector = new ActualParamsCollector();
        node.getActualParams().apply(paramsCollector);
        Deque<Value> actualParams = new ArrayDeque();
        for(PExpr curParam : paramsCollector.getExprs()) {
            actualParams.push(value.pop());
        }

        // Checker ensures the function exists
        Type returnType = Util.functionReturnType(functions.getByName(funcName));
        if (Util.isPure(functions.getByName(funcName))) {
            pushPureFuncExpr(funcName, actualParams, returnType);
        } else {
            pushProcFuncExpr(funcName, actualParams, returnType);
        }
    }

    @Override
    public void outAInvokeExpr(AInvokeExpr node) {
        ActualParamsCollector paramsCollector = new ActualParamsCollector();
        node.getActualParams().apply(paramsCollector);
        String methodName = node.getIdentifier().getText().trim();
        String boogieProcTarget = methods.getBoogieProcName(methodName);

        // we will replace the object memory for the method call, but parameters may use the current object memory
        // so first put all parameters in temporaries
        Deque<String> actualParams = new ArrayDeque();
        for(PExpr curParam : paramsCollector.getExprs()) {
            String paramTemp = newTemporary(BoogieType.Opaque);
            out.println(paramTemp + " := " + value.pop().toOpaqueString() + ";");
            actualParams.push(paramTemp);
        }

        Value targetValue = value.pop();

        // target object ID is passed to method, so that needs a temporary, too
        String targetObjectId = newTemporary("ObjectId");
        out.println(targetObjectId + " := " + targetValue.toObjectIdString() + ";");

        String tmpInvokeResult = newTemporary(BoogieType.Opaque);
        out.println(tmpInvokeResult + " := defaultValue;");

        out.println("if(objectValid(heap, " + targetObjectId + ")) {");
        out.pushTab();

        String savObjectMemory = newTempMemory();
        out.println(savObjectMemory + ":= objectMemory;");

        String splitHeapId = newTemporary("SplitHeap");
        lastSplitHeapId = splitHeapId;

        out.println(splitHeapId + " := invokeSplit(heap, " + targetValue.toObjectIdString() + ");");

        out.println("heap := " + splitHeapId + "[heapRight];");
        out.println("objectMemory := " + splitHeapId + "[targetObject][objectMemoryAttr];");

        StringBuffer paramsString = new StringBuffer();
        for(PExpr curParam : paramsCollector.getExprs()) {
            paramsString.append("," + actualParams.pop());
        }

        PInvokeClass targetClass = node.getInvokeClass();
        if (targetClass != null) {
            AInvokeClass invokeClass = (AInvokeClass)targetClass;
            IdentifierCollector invokeClassCol = new IdentifierCollector();
            invokeClass.apply(invokeClassCol);

            boolean first = true;
            for(String targetClassId : invokeClassCol.getIds()) {
                if (!first) {
                    out.print("else ");
                }
                out.println("if (" + splitHeapId + "[targetObject][objectClassIdAttr] == " + targetClassId + ") {");
                out.pushTab();
                out.print("call " + getLookupString(tmpInvokeResult) + ":= " + targetClassId + "." + methodName + "(" + splitHeapId + "[targetObject][objectClassIdAttr], " + targetObjectId);
                out.print(paramsString.toString());
                out.println(");");
                first = false;
                out.popTab();
                out.println("}");
            }

            out.println("else {");
            out.pushTab();
        }

        out.print("call " + getLookupString(tmpInvokeResult) + ":=" + boogieProcTarget + "(" + splitHeapId + "[targetObject][objectClassIdAttr], " + targetObjectId);
        out.print(paramsString.toString());
        out.println(");");

        if (targetClass != null) {
            out.popTab();
            out.println("}");
        }

        out.println(splitHeapId + "[targetObject][objectMemoryAttr] := objectMemory;");
        out.println(splitHeapId + "[heapRight] := heap;");
        out.println("objectMemory := " + savObjectMemory + ";");
        out.println("heap := assembleHeap(" + splitHeapId + ");");

        out.println("}");
        out.popTab();

        value.push(new Value(BoogieType.Opaque, getLookupString(tmpInvokeResult)));

    }

    @Override
    public void outAAssignAssignExpr(AAssignAssignExpr node) {

        Value rvalue = value.pop();
        String lvalue = node.getIdentifier().getText().trim();
        letDecls.add(lvalue);
        out.println(getLookupString(lvalue) + ":=" + rvalue.toOpaqueString() + ";");
        value.push(rvalue);
        // TODO: Do we need to infer types for let bound variables?
        symbolTable.addSymbol(lvalue, Type.Opaque);
    }

    @Override
    public void outANotBoolExpr(ANotBoolExpr node) {
        Value v = value.pop();
        value.push(new Value(BoogieType.Boolean, "!(" + v.toBooleanString() + ")"));
    }

    private void doMapAssign(Deque<Value> assignStack, int num) {
        if (num == 0) {
            out.println(assignStack.pop().toOpaqueString() + " := " + assignStack.pop().toOpaqueString() + ";");
        } else {
            // the item on the top of the stack is a Boogie lvalue
            Value lvalue = assignStack.pop();
            // convert it to a map using a temporary
            String temp = getLookupString(newTempMemory());
            out.println(temp + " := toMemory(" + lvalue.toOpaqueString() + ");");
            // top of the stack is the map index
            Value mapIndex = assignStack.pop();
            // make a new lvalue using the temporary and put it on the stack
            assignStack.push(new Value(BoogieType.Opaque, temp + "[" + mapIndex.toBitstringString() + "]"));

            doMapAssign(assignStack, num - 1);

            // assign the temporary back to the original lvalue
            out.println(lvalue.toOpaqueString() + " := fromMemory(" + temp + ");");
        }
    }

    @Override
    public void caseANewExpr(ANewExpr newExpr) {

        newExpr.getNewParamsList().apply(this);

        NewParamsCollector col = new NewParamsCollector();
        newExpr.getNewParamsList().apply(col);
        Deque<Value> actualParams = new ArrayDeque();
        for(int i = 0; i < col.getParams().size(); i++) {
            actualParams.push(value.pop());
        }

        // call the converted constructor and push the resulting reference
        String className = classes.getByDef(newExpr);
        String objectId = newTemporary("Object");
        out.print("call " + objectId + " := " + className + ".new(");
        boolean first = true;
        for(Value curActual : actualParams) {
            if (!first) {
                out.print(",");
            }
            out.print(curActual.toOpaqueString());
            first = false;
        }
        out.println(");");

        out.println(objectId + "[objectIdAttr] := freshObjectId(internal.objectId, heap);");

        // add the new object to the heap
        out.println("heap := addObject(heap, " + objectId + ");");

        value.push(new Value(BoogieType.ObjectId, objectId + "[objectIdAttr]"));
    }

    @Override
    public void outAQuantPrimaryExpr(AQuantPrimaryExpr node) {

        PExprLabel label = node.getExprLabel();
        if (label != null) {
            AExprLabel aLabel = (AExprLabel)label;

            String labelStr = aLabel.getIdentifier().getText().trim();
            boolean isInvoke = Util.toInvokeExpr(node.getExpr()).isPresent();
            if(isInvoke) {
                BoogieUtil.saveCheckpoint(out, labelStr, value.peek().toOpaqueString(), lastSplitHeapId);
            } else {
                BoogieUtil.saveCheckpoint(out, labelStr, value.peek().toOpaqueString());
            }

            checkpoints.put(labelStr, new Checkpoint(labelStr, isInvoke));

        }

    }

    @Override
    public void outARefPrimaryExpr(ARefPrimaryExpr node) {
        String refValue = value.peek().toObjectIdString();
        out.println("if (!objectValid(heap, " + refValue + ")) {");
        out.pushTab();
        out.println("heap := addObject(heap, emptyObject[objectIdAttr := " + refValue + "]);");
        out.popTab();
        out.println("}");
    }

}
