/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.visitor.ActualParamsCollectorBounds;
import com.amazon.quivela.checker.visitor.FormalParamsCollector;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

public class BoogieBoundsExprConverter extends AnalysisAdapter {

    private final SymbolTable symbolTable;
    private final BoogieFunctions functions;
    private final BoogieConstants constants;
    private final PrettyPrintStream out;

    private Deque<Value> value = new ArrayDeque();

    public BoogieBoundsExprConverter(SymbolTable symbolTable, BoogieFunctions functions, BoogieConstants constants, PrettyPrintStream out) {
        this.symbolTable = symbolTable;
        this.functions = functions;
        this.constants = constants;
        this.out = out;

    }

    @Override
    public void defaultCase(Node node)
    {
        throw new RuntimeException("Conversion of " + node.getClass() + " not implemented.");
    }

    public Value getValue() {
        if (value.size() > 1) {
            throw new RuntimeException("Values lost during conversion");
        }

        return value.peek();
    }

    @Override
    public void caseABoundsExpr(ABoundsExpr node) {
        node.getSumBoundsExpr().apply(this);
    }

    @Override
    public void caseAProductBoundsExprSumBoundsExpr(AProductBoundsExprSumBoundsExpr node) {
        node.getProductBoundsExpr().apply(this);
    }

    @Override
    public void caseAExponentBoundsExprProductBoundsExpr(AExponentBoundsExprProductBoundsExpr node) {
        node.getExponentBoundsExpr().apply(this);
    }

    @Override
    public void caseAPrimaryBoundsExprExponentBoundsExpr(APrimaryBoundsExprExponentBoundsExpr node) {
        node.getPrimaryBoundsExpr().apply(this);
    }

    @Override
    public void caseALiteralPrimaryBoundsExpr(ALiteralPrimaryBoundsExpr node) {
        node.getLiteral().apply(this);
    }

    @Override
    public void caseANumericLiteral(ANumericLiteral node) {
        node.getNumericLiteral().apply(this);
    }

    @Override
    public void caseABoolLiteral(ABoolLiteral node) {
        node.getBoolLiteral().apply(this);
    }

    @Override
    public void caseTNumericLiteral(TNumericLiteral node) {
        value.push(new Value(BoogieType.Integer, node.getText()));
    }

    @Override
    public void caseTBoolLiteral(TBoolLiteral node) {
        value.push(new Value(BoogieType.Boolean, node.getText()));
    }


    @Override
    public void caseABinOpSumBoundsExpr(ABinOpSumBoundsExpr node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightValue = value.pop();
        Value leftValue = value.pop();

        String op = node.getSumOp().getText();
        BoogieType type = BoogieType.Integer;
        if (leftValue.getType() == BoogieType.Real || rightValue.getType() == BoogieType.Real) {
            type = BoogieType.Real;
        }

        value.push(new Value(type, "(" + leftValue.getValue(type)+ convertOperator(op) + rightValue.getValue(type) + ")"));
    }

    @Override
    public void caseAQuantPrimaryBoundsExpr(AQuantPrimaryBoundsExpr node) {
        node.getBoundsExpr().apply(this);
    }

    @Override
    public void caseAFuncExprPrimaryBoundsExpr(AFuncExprPrimaryBoundsExpr node) {
        node.getFuncBoundsExpr().apply(this);
    }

    private void pushPureFuncExpr(String funcName, Collection<String> actualParams, Type returnType) {

        StringBuffer newVal = new StringBuffer(funcName + "(");
        boolean first = true;
        for(String curParam : actualParams) {
            if (!first) {
                newVal.append(",");
            }
            newVal.append(curParam);
            first = false;
        }

        newVal.append(")");

        value.push(new Value(BoogieUtil.toBoogieType(returnType), newVal.toString()));
    }

    @Override
    public void caseAFuncBoundsExpr(AFuncBoundsExpr node) {
        String funcName = node.getIdentifier().getText().trim();
        AFuncDecl func = functions.getByName(funcName);
        if (func == null) {
            throw new RuntimeException("function not declared: " + funcName);
        }

        FormalParamsCollector formalParams = new FormalParamsCollector();
        func.getFormalParamsList().apply(formalParams);

        ActualParamsCollectorBounds paramsCollector = new ActualParamsCollectorBounds();
        node.getBoundsActualParams().apply(paramsCollector);
        Deque<String> actualParamsReverse = new ArrayDeque();
        int paramIndex = 0;
        for(PBoundsExpr curParam : paramsCollector.getExprs()) {
            curParam.apply(this);
            Type paramType = formalParams.getTypes().get(paramIndex);
            actualParamsReverse.push(value.pop().getValue(BoogieUtil.toBoogieType(paramType)));
            paramIndex++;
        }

        Deque<String> actualParams = new ArrayDeque();
        while(!actualParamsReverse.isEmpty()) {
            actualParams.push(actualParamsReverse.pop());
        }


        Type returnType = Util.functionReturnType(func);
        if (Util.isPure(functions.getByName(funcName))) {
            pushPureFuncExpr(funcName, actualParams, returnType);
        } else {
            // TOOD: better error handling
            throw new RuntimeException("Only pure functions allowed in bounds expressions.");
        }
    }

    @Override
    public void caseALookupPrimaryBoundsExpr(ALookupPrimaryBoundsExpr node) {
        String id = node.getIdentifier().getText();
        value.push(new Value(BoogieUtil.toBoogieType(symbolTable.getType(id)), id));
    }

    @Override
    public void caseAEnvPrimaryBoundsExpr(AEnvPrimaryBoundsExpr node) {

        String exprConst = constants.addExpr(node.toString(), out);

        value.push(new Value(BoogieType.Expr, exprConst));
    }

    @Override
    public void caseABinOpProductBoundsExpr(ABinOpProductBoundsExpr node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightValue = value.pop();
        Value leftValue = value.pop();

        String op = node.getProductOp().getText();
        BoogieType type = BoogieType.Integer;
        if (op.equals("/")) {
            type = BoogieType.Real;
        } else {
            if (leftValue.getType() == BoogieType.Real || rightValue.getType() == BoogieType.Real) {
                type = BoogieType.Real;
            }
        }

        value.push(new Value(type, "(" + leftValue.getValue(type) + convertOperator(op) + rightValue.getValue(type) + ")"));
    }

    @Override
    public void caseABinOpExponentBoundsExpr(ABinOpExponentBoundsExpr node) {
        node.getLeft().apply(this);
        node.getRight().apply(this);

        Value rightValue = value.pop();
        Value leftValue = value.pop();

        String op = node.getExponentOp().getText();

        value.push(new Value(BoogieType.Real, "real_pow(" + leftValue.toRealString() + "," + rightValue.toRealString() + ")"));
    }

    private static String convertOperator(String in) {
        if (in.equals("^")) {
            return "**";
        } else {
            return in;
        }
    }
}
