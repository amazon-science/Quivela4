/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.visitor.ActualParamsCollectorProp;
import com.amazon.quivela.checker.visitor.FormalParamsCollector;
import com.amazon.quivela.checker.visitor.IdentifierCollector;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.*;

public class BoogiePropConverter extends AnalysisAdapter {


    private final SymbolTable symbolTable;
    private final BoogieConstants constants;
    private final Map<String, ANewExpr> classes;
    private final BoogieFunctions functions;
    Deque<Value> value = new ArrayDeque();
    private Map<String, String> varMap;
    private final PrettyPrintStream out;

    private final String leftHeap;
    private final String rightHeap;
    private final String leftObjectMem;
    private final String rightObjectMem;
    private final Collection<String> leftObjectFields;
    private final Collection<String> rightObjectFields;

    public BoogiePropConverter(SymbolTable symbolTable, BoogieConstants constants, Map<String, ANewExpr> classes, BoogieFunctions functions, Map<String, String> varMap, PrettyPrintStream out, String leftHeap, String rightHeap, String leftObjectMem, String rightObjectMem, Collection<String> leftObjectFields, Collection<String> rightObjectFields) {
        this.symbolTable = symbolTable;
        this.constants = constants;
        this.classes = classes;
        this.functions = functions;
        this.varMap = varMap;
        this.out = out;

        this.leftHeap = leftHeap;
        this.rightHeap = rightHeap;
        this.leftObjectMem = leftObjectMem;
        this.rightObjectMem = rightObjectMem;
        this.leftObjectFields = leftObjectFields;
        this.rightObjectFields = rightObjectFields;

    }


    public BoogiePropConverter(SymbolTable symbolTable, BoogieConstants constants, Map<String, ANewExpr> classes, BoogieFunctions functions, Map<String, String> varMap, PrettyPrintStream out) {
        this(symbolTable, constants, classes, functions, varMap, out, null, null, null, null, null, null);
    }



    public String getValueString() {

        return getValue().toBooleanString();
    }

    private String getLookupString(String ident) {
        String mapped = varMap.get(ident);
        return mapped == null ? ident : mapped;
    }

    public Value getValue() {
        if(value.size() != 1) {
            throw new RuntimeException("Error when converting proposition to Boogie");
        }
        return value.peek();
    }

    @Override
    public void defaultCase(Node node)
    {
        throw new RuntimeException("Conversion of " + node.getClass() + " not implemented");
    }

    @Override
    public void caseAFieldsEqualPositionalPrimaryProp(AFieldsEqualPositionalPrimaryProp node) {
        StringBuffer buf = new StringBuffer();
        boolean first = true;
        Iterator<String> rightFields = rightObjectFields.iterator();
        for(String curLeftField : leftObjectFields) {
            if (rightFields.hasNext()) {
                String curRightField = rightFields.next();
                if (!first) {
                    buf.append (" && ");
                }
                buf.append(leftObjectMem + "[internal.attribute.field." + curLeftField + "] == " + rightObjectMem + "[internal.attribute.field." + curRightField + "]");
                first = false;
            }
        }

        value.push(new Value(BoogieType.Boolean, buf.toString()));
    }

    @Override
    public void caseAFieldsEqualExceptPrimaryProp(AFieldsEqualExceptPrimaryProp node) {

        Collection<String> ids = new ArrayList();

        if(node.getIdentifierList() != null) {
            IdentifierCollector col = new IdentifierCollector();
            node.getIdentifierList().apply(col);
            ids = col.getIds();
        }

        StringBuffer buf = new StringBuffer();
        boolean first = true;
        for(String curField : leftObjectFields) {
            if (rightObjectFields.contains(curField) && !ids.contains(curField)) {
                if (!first) {
                    buf.append (" && ");
                }
                buf.append(leftObjectMem + "[internal.attribute.field." + curField + "] == " + rightObjectMem + "[internal.attribute.field." + curField + "]");
                first = false;
            }
        }

        if (first) {
            throw new RuntimeException("No common fields remain.");
        }

        value.push(new Value(BoogieType.Boolean, buf.toString()));
    }

    @Override
    public void caseATobitsPrimaryProp(ATobitsPrimaryProp node) {
        node.getProp().apply(this);
        value.push(new Value(BoogieType.Bitstring, value.pop().toBitstringString()));
    }

    @Override
    public void caseAIsbitsPrimaryProp(AIsbitsPrimaryProp node) {
        node.getProp().apply(this);
        String v = value.pop().toOpaqueString();
        value.push(new Value(BoogieType.Boolean, "isBits(" + v + ")"));
    }

    @Override
    public void caseAEnvPrimaryProp(AEnvPrimaryProp node) {

        String exprConst = constants.addExpr(node.toString(), out);
        value.push(new Value(BoogieType.Expr, exprConst));
    }

    @Override
    public void caseAObjEqPrimaryProp(AObjEqPrimaryProp node) {
        node.getObjEqProp().apply(this);
    }

    @Override
    public void caseAObjEqProp(AObjEqProp node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightValue = value.pop();
        Value leftValue = value.pop();

        value.push(new Value(BoogieType.Boolean, leftValue.getValue() + "==" + rightValue.getValue()));
    }

    @Override
    public void caseALiteralPrimaryProp(ALiteralPrimaryProp node) {
        node.getLiteral().apply(this);
    }


    @Override
    public void caseAArithProp(AArithProp node) {
        node.getSumProp().apply(this);
    }

    @Override
    public void caseAProductSumProp(AProductSumProp node) {
        node.getProductProp().apply(this);
    }

    @Override
    public void caseASumSumProp(ASumSumProp node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value right = value.pop();
        Value left = value.pop();

        value.push(new Value(BoogieType.Integer, "(" + left.getValue(BoogieType.Integer) + ")" + convertSumOp(node.getSumOp()) + "(" + right.getValue(BoogieType.Integer) + ")"));
    }

    @Override
    public void caseAProductProductProp(AProductProductProp node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value right = value.pop();
        Value left = value.pop();

        value.push(new Value(BoogieType.Integer, "(" + left.getValue(BoogieType.Integer) + ")" + convertProductOp(node.getProductOp()) + "(" + right.getValue(BoogieType.Integer) + ")"));
    }


    @Override
    public void caseAIndependencePrimaryProp(AIndependencePrimaryProp node) {
        node.getHeap().apply(this);
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightValue = value.pop();
        Value leftValue = value.pop();
        Value heapValue = value.pop();

        value.push(new Value(BoogieType.Boolean, "objectValid(" + heapValue.getValue() + ", " + leftValue.toObjectIdString() + ") ==> objectValid(invokeSplit(" + heapValue.getValue() + ", " + rightValue.toObjectIdString() + ")[heapLeft]," + leftValue.toObjectIdString() + ")"));
    }

    @Override
    public void caseAObjectIsPrimaryProp(AObjectIsPrimaryProp node) {

        BoogieClassIdCollector col = new BoogieClassIdCollector();
        node.getClassIdProp().apply(col);

        String type = col.getClassId();

        node.getObjectProp().apply(this);
        Value objectValue = value.pop();

        value.push(new Value(BoogieType.Boolean, objectValue.getValue() + "[objectClassIdAttr] == " + type));
    }

    @Override
    public void caseAPropsTl(APropsTl node) {
        node.getProp().apply(this);
    }

    @Override
    public void caseAFrameAllPrimaryProp(AFrameAllPrimaryProp node) {
        value.push(new Value(BoogieType.Boolean, leftHeap + "==" + rightHeap));
    }


    @Override
    public void caseAFramePrimaryProp(AFramePrimaryProp node) {
        node.getFrameProp().apply(this);
    }

    @Override
    public void caseAFrameHeapPrimaryProp(AFrameHeapPrimaryProp node) {
        node.getFrameHeapProp().apply(this);
    }

    @Override
    public void caseAFrameProp(AFrameProp node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightVal = value.pop();
        Value leftVal = value.pop();

        value.push(new Value(BoogieType.Boolean, "frame(" + leftHeap + "," + rightHeap + ", " + leftVal.toObjectIdString() + "," + rightVal.toObjectIdString() + ")"));
    }

    @Override
    public void caseAFromHeapHeapProp(AFromHeapHeapProp node) {
        node.getHeapProp().apply(this);
        node.getProp().apply(this);

        Value refValue = value.pop();
        Value heapValue = value.pop();

        value.push(new Value(BoogieType.Heap, "invokeSplit(" + heapValue.getValue() + "," + refValue.toObjectIdString() + ")[heapRight]"));
    }

    @Override
    public void caseAFrameHeapProp(AFrameHeapProp node) {

        node.getLeftHeap().apply(this);
        node.getRightHeap().apply(this);

        Value rightHeap = value.pop();
        Value leftHeap = value.pop();

        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightVal = value.pop();
        Value leftVal = value.pop();

        value.push(new Value(BoogieType.Boolean, "frame(" + leftHeap.getValue() + "," + rightHeap.getValue() + ", " + leftVal.toObjectIdString() + "," + rightVal.toObjectIdString() + ")"));
    }

    @Override
    public void caseAObjLrObjectProp(AObjLrObjectProp node) {
        node.getObjLr().apply(this);
    }

    @Override
    public void caseAObjLr(AObjLr node) {
        String objIn = node.getLeftOrRight().getText().trim();
        if (objIn.equals("left")) {
            value.push(new Value(BoogieType.Memory, leftObjectMem));
        } else if (objIn.equals("right")) {
            value.push(new Value(BoogieType.Memory, rightObjectMem));
        } else {
            throw new RuntimeException("invalid object specifier: " + objIn);
        }
    }

    @Override
    public void caseAHeapLrHeapProp(AHeapLrHeapProp node) {
        node.getHeapLr().apply(this);
    }

    @Override
    public void caseAHeapLr(AHeapLr node) {
        String objIn = node.getLeftOrRight().getText().trim();
        if (objIn.equals("left")) {
            value.push(new Value(BoogieType.Heap, leftHeap));
        } else if (objIn.equals("right")) {
            value.push(new Value(BoogieType.Heap, rightHeap));
        } else {
            throw new RuntimeException("invalid heap specifier: " + objIn);
        }
    }

    @Override
    public void caseAFromHeapObjectProp(AFromHeapObjectProp node) {

        node.getHeapProp().apply(this);
        String boogieHeap = value.pop().getValue();

        node.getProp().apply(this);
        Value v = value.pop();
        value.push(new Value(BoogieType.Object, "invokeSplit(" + boogieHeap + "," + v.toObjectIdString() + ")[targetObject]"));
    }

    @Override
    public void caseAProp(AProp prop) {
        prop.getForallExistsProp().apply(this);
    }
    @Override
    public void caseALogicForallExistsProp(ALogicForallExistsProp node) {
        node.getLogicProp().apply(this);
    }
    @Override
    public void caseABoolLogicProp(ABoolLogicProp term) {
        term.getBoolProp().apply(this);
    }

    @Override
    public void caseAPrimaryProductProp(APrimaryProductProp term) {
        term.getPrimaryProp().apply(this);
    }

    @Override
    public void caseALookupPrimaryProp(ALookupPrimaryProp term) {
        String id = term.getIdentifier().getText().trim();
        Type type = symbolTable.getType(id);
        if (type == null) {
            throw new RuntimeException("symbol does not exist: " + id);
        }

        BoogieType boogieType = BoogieUtil.toBoogieType(type);
        value.push(new Value(boogieType, getLookupString(id)));
    }
    @Override
    public void caseAFuncPrimaryProp(AFuncPrimaryProp node) {
        node.getFuncProp().apply(this);
    }
    @Override
    public void caseAQuantPrimaryProp(AQuantPrimaryProp node) {
        node.getProp().apply(this);

        Value topValue = value.pop();
        value.push(new Value(topValue.getType(), "(" + topValue.getValue() + ")"));
    }
    @Override
    public void caseAMapLookupPrimaryProp(AMapLookupPrimaryProp node) {
        node.getMap().apply(this);
        node.getIndex().apply(this);

        Value index = value.pop();
        Value map = value.pop();

        value.push(new Value(BoogieType.Opaque, map.toMemoryString() + "[" + index.toBitstringString() + "]"));
    }
    @Override
    public void caseAMapUpdatePrimaryProp(AMapUpdatePrimaryProp node) {
        node.getMap().apply(this);
        node.getIndex().apply(this);
        node.getValue().apply(this);

        Value v = value.pop();
        Value index = value.pop();
        Value map = value.pop();

        value.push(new Value(BoogieType.Memory, map.toMemoryString() + "[" + index.toBitstringString() + " := "  + v.toOpaqueString() + "]"));
    }

    @Override
    public void caseAFuncProp(AFuncProp node) {

        node.getActualParamsProp().apply(this);

        String funcName = node.getIdentifier().getText().trim();
        AFuncDecl funcDecl = functions.getByName(funcName);
        if (funcDecl == null) {
            throw new RuntimeException("Function not declared: " + funcName);
        }
        List<Type> parameterTypes = Util.functionParameterTypes(funcDecl);

        ActualParamsCollectorProp paramsCollector = new ActualParamsCollectorProp();
        node.getActualParamsProp().apply(paramsCollector);
        Deque<Value> actualParams = new ArrayDeque();
        for(PProp curParam : paramsCollector.getProps()) {
            actualParams.push(value.pop());
        }

        StringBuffer newVal = new StringBuffer(funcName + "(");
        boolean first = true;
        int i = 0;
        for(Value curParam : actualParams) {
            if (!first) {
                newVal.append(",");
            }
            newVal.append(curParam.getValue(BoogieUtil.toBoogieType(parameterTypes.get(i))));
            first = false;
            i++;
        }

        newVal.append(")");

        Type returnType = Util.functionReturnType(funcDecl);
        value.push(new Value(BoogieUtil.toBoogieType(returnType), newVal.toString()));

    }

    @Override
    public void caseAActualParamsProp(AActualParamsProp node) {
        node.getProps().apply(this);
    }
    @Override
    public void caseAProps(AProps node) {
        node.getProp().apply(this);
        for (Object o : node.getPropsTl()) {
            Node n = (Node) o;
            n.apply(this);
        }
    }


    @Override
    public void caseANumericLiteral(ANumericLiteral literal) {
        String literalValue = literal.getNumericLiteral().toString().trim();
        value.push(new Value(BoogieType.Integer, literalValue));
    }

    @Override
    public void caseABoolLiteral(ABoolLiteral literal) {
        String literalValue = literal.getBoolLiteral().toString().trim();
        value.push(new Value(BoogieType.Boolean, literalValue));
    }

    @Override
    public void caseAObjectLookupPrimaryProp(AObjectLookupPrimaryProp node) {
        node.getObjectProp().apply(this);
        String varName = node.getIdentifier().getText().trim();

        Value object = value.pop();
        value.push(new Value(BoogieType.Opaque, object.toMemoryString() + "[internal.attribute.field." + varName +"]"));

    }

    @Override
    public void caseAArithBoolProp(AArithBoolProp node) {
        node.getArithProp().apply(this);
    }

    /*
    @Override
    public void outALookupPrimaryProp(ALookupPrimaryProp node) {
        TIdentifier ident = node.getIdentifier();
        String identString = ident.getText().trim();

        ObjectScope scope = objectScope.peek();
        if (scope == null) {
            value.push(new Value(Type.Opaque, identString));
        } else {
            value.push(new Value(Type.Opaque, scope.getField("internal.attribute.field." + identString)));
        }

    }

    @Override
    public void outAQuantPrimaryProp(AQuantPrimaryProp node) {
        Value topValue = value.pop();
        value.push(new Value(topValue.getType(), "(" + topValue.getValueString() + ")"));
    }

    @Override
    public void caseTBisimPropObject(TBisimPropObject obj) {
        value.push(new Value(Type.Opaque, obj.getText().trim()));
    }
    */

    /*
    @Override
    public void caseAMemberMemberProp(AMemberMemberProp node) {

        node.getRef().apply(this);
        Value scopeValue = value.pop();

        ObjectScope scope = objectScope.peek();
        if (scope == null) {
            if(scopeValue.getValueString().equals("left")) {
                ObjectScope leftScope = new ObjectScope("heap1", "objectMemory1");
                objectScope.push(leftScope);
            } else if (scopeValue.getValueString().equals("right")) {
                ObjectScope rightScope = new ObjectScope("heap2", "objectMemory2");
                objectScope.push(rightScope);
            } else {
                throw new RuntimeException(new CheckException(node.getLCurl(), "Invalid top-level object identifier: " + scopeValue.getValueString()));
            }
        } else {
            String ref = "toObjectId(" + scope.getField(scopeValue.toOpaqueString()) + ")";
            ObjectScope newScope = new ObjectScope(scope.getHeap(), "invokeSplit(" + scope.getHeap() + "," + ref + ")[targetObject][objectMemoryAttr]");
            objectScope.push(newScope);
        }

        node.getLocalExpr().apply(this);

        objectScope.pop();
    }
    */


    @Override
    public void caseARelOpBoolProp(ARelOpBoolProp node) {

        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value right = value.pop();
        Value left = value.pop();
        String opStr = node.getPropRelOp().toString().trim();
        BoogieType inType = BoogieUtil.getInType(opStr, left.getType(), right.getType());
        value.push(new Value(BoogieType.Boolean, "(" + left.getValue(inType) + ")" + BoogieUtil.convertBoolOp(opStr) + "(" + right.getValue(inType) + ")"));

    }
/*
    @Override
    public void outAMapLookupPrimaryProp(AMapLookupPrimaryProp node) {
        Value index = value.pop();
        Value map = value.pop();

        value.push(new Value(Type.Opaque, "toMemory(" + map.getValueString() + ")[" + index.getValueString() + "]"));
    }

*/

    @Override
    public void caseAForallExistsForallExistsProp(AForallExistsForallExistsProp node) {
        FormalParamsCollector collector = new FormalParamsCollector();
        node.getFormalParamsList().apply(collector);
        List<String> params = collector.getIds();
        List<Type> types = collector.getTypes();
        for(int i = 0; i < types.size(); i++) {
            if (types.get(i) == null) {
                types.set(i, Type.Bitstring);
            }
        }

        symbolTable.pushFrame(true);
        symbolTable.addAll(params, types);

        node.getProp().apply(this);

        Value v = value.pop();

        StringBuffer valueBuf = new StringBuffer();

        if (node.getForallExists() instanceof AForallForallExists) {
            valueBuf.append("forall ");
        } else if (node.getForallExists() instanceof AExistsForallExists) {
            valueBuf.append("exists ");
        } else {
            throw new RuntimeException("Unexpected: " + node.getForallExists());
        }


        boolean first = true;
        int i = 0;
        for(String curParam : params) {
            if (!first) {
                valueBuf.append(",");
            }
            first = false;
            String strType = BoogieUtil.toBoogieType(types.get(i)).getBoogieString();
            valueBuf.append(curParam + ":" + strType);
            i++;
        }
        valueBuf.append(":: ");
        valueBuf.append(v.toBooleanString());

        value.push(new Value(BoogieType.Boolean, valueBuf.toString()));

        symbolTable.popFrame();

    }

    @Override
    public void caseALogicOpLogicProp(ALogicOpLogicProp node) {

        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value right = value.pop();
        Value left = value.pop();
        value.push(new Value(BoogieType.Boolean, "(" + left.toBooleanString() + ")" + convertPropLogicOp(node.getPropLogicOp()) + "(" + right.toBooleanString() + ")"));
    }

    @Override
    public void caseANotLogicProp(ANotLogicProp node) {
        node.getBoolProp().apply(this);
        Value propValue = value.pop();
        value.push(new Value(BoogieType.Boolean, "!(" + propValue.toBooleanString() + ")"));
    }

    private String convertTermLogicOp(TLogicOp op) {

        String opStr = op.getText().trim();

        if (opStr.equals("&")) {
            return "&&";
        } else if (opStr.equals("|")) {
            return "||";
        } else {
            throw new RuntimeException("unexpected logic op: " + opStr);
        }
    }

    private String convertPropLogicOp(TPropLogicOp op) {

        String opStr = op.getText().trim();

        if (opStr.equals("&&")) {
            return "&&";
        } else if (opStr.equals("||")) {
            return "||";
        } else if (opStr.equals("->")) {
            return "==>";
        } else {
            throw new RuntimeException("unexpected logic op: " + opStr);
        }
    }

    private String convertSumOp(TSumOp op) {

        String opStr = op.getText().trim();

        if (opStr.equals("+")) {
            return "+";
        } else if (opStr.equals("-")) {
            return "-";
        } else {
            throw new RuntimeException("unimplemented logic op: " + opStr);
        }
    }

    private String convertProductOp(TProductOp op) {

        String opStr = op.getText().trim();

        if (opStr.equals("%")) {
            return " mod ";
        } else if (opStr.equals("*")) {
            return "*";
        } else if (opStr.equals("/")) {
            return "/";
        } else {
            throw new RuntimeException("unimplemented logic op: " + opStr);
        }
    }
}
