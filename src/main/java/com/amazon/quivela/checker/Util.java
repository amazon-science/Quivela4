/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker;

import com.amazon.quivela.checker.visitor.FormalParamsCollector;
import com.amazon.quivela.checker.visitor.IdentifierAndTypeExtractor;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Util {


    private static class IsPrimary extends AnalysisAdapter {
        private Optional<PPrimaryExpr> primary = Optional.empty();

        @Override
        public void caseAExpr(AExpr node) {
            node.getLogicExpr().apply(this);
        }

        @Override
        public void caseAAssignExprLogicExpr(AAssignExprLogicExpr node) {
            node.getAssignExpr().apply(this);
        }

        @Override
        public void caseABoolAssignExpr(ABoolAssignExpr node) {
            node.getBoolExpr().apply(this);
        }

        public void caseAArithExprBoolExpr(AArithExprBoolExpr node) {
            node.getArithExpr().apply(this);
        }

        @Override
        public void caseAArithExpr(AArithExpr node) {
            node.getSumExpr().apply(this);
        }

        @Override
        public void caseAProductSumExpr(AProductSumExpr node) {
            node.getProductExpr().apply(this);
        }

        @Override
        public void caseAPrimaryExprProductExpr(APrimaryExprProductExpr node) {
            primary = Optional.of(node.getPrimaryExpr());
        }

        public Optional<PPrimaryExpr> getPrimary() {
            return primary;
        }
    }

    public static Node exprNodeToBoundsExprNode(Node node) {
        if (node instanceof ASumSumExpr) {
            ASumSumExpr sumExpr = (ASumSumExpr)node;
            return new ABinOpSumBoundsExpr((PProductBoundsExpr)exprNodeToBoundsExprNode(sumExpr.getLeft()), sumExpr.getSumOp(), (PSumBoundsExpr)exprNodeToBoundsExprNode(sumExpr.getRight()));
        } else if (node instanceof AProductSumExpr) {
            AProductSumExpr expr = (AProductSumExpr)node;
            return new AProductBoundsExprSumBoundsExpr((PProductBoundsExpr)exprNodeToBoundsExprNode(expr.getProductExpr()));
        } else if (node instanceof APrimaryExprProductExpr) {
            APrimaryExprProductExpr expr = (APrimaryExprProductExpr)node;
            return new AExponentBoundsExprProductBoundsExpr(new APrimaryBoundsExprExponentBoundsExpr((PPrimaryBoundsExpr)exprNodeToBoundsExprNode(expr.getPrimaryExpr())));
        } else if (node instanceof ALookupPrimaryExpr) {
            ALookupPrimaryExpr expr = (ALookupPrimaryExpr)node;
            return new ALookupPrimaryBoundsExpr(expr.getIdentifier());
        } else if (node instanceof ALiteralPrimaryExpr) {
            ALiteralPrimaryExpr expr = (ALiteralPrimaryExpr)node;
            return new ALiteralPrimaryBoundsExpr(expr.getLiteral());
        } else {
            throw new RuntimeException("Unable to convert " + node.getClass() + " to bounds expr node");
        }
    }

    public static PBoundsExpr toBoundsExpr(Node node) {
        if (node instanceof PSumBoundsExpr) {
            PSumBoundsExpr expr = (PSumBoundsExpr)node;
            return new ABoundsExpr(expr);
        } else if (node instanceof PSumBoundsExpr) {
            PSumBoundsExpr expr = (PSumBoundsExpr)node;
            return new ABoundsExpr(expr);
        }else {
            throw new RuntimeException("Unable to convert " + node.getClass() + " to bounds expr");
        }
    }

    public static Optional<ANewExpr> toNewExpr(PExpr expr) {
        IsPrimary p = new IsPrimary();
        expr.apply(p);
        if (p.getPrimary().isPresent()) {
            PPrimaryExpr primary = p.getPrimary().get();
            if (primary instanceof ANewExprPrimaryExpr) {
                return Optional.of((ANewExpr)((ANewExprPrimaryExpr)primary).getNewExpr());
            }
        }
        return Optional.empty();
    }

    public static Optional<AInvokeExpr> toInvokeExpr(PExpr expr) {
        IsPrimary p = new IsPrimary();
        expr.apply(p);
        if (p.getPrimary().isPresent()) {
            PPrimaryExpr primary = p.getPrimary().get();
            if (primary instanceof AInvokeExprPrimaryExpr) {
                return Optional.of((AInvokeExpr)((AInvokeExprPrimaryExpr)primary).getInvokeExpr());
            }
        }
        return Optional.empty();
    }

    public static Optional<AQuantPrimaryExpr> toQuantPrimaryExpr(PExpr expr) {
        IsPrimary p = new IsPrimary();
        expr.apply(p);
        if (p.getPrimary().isPresent()) {
            PPrimaryExpr primary = p.getPrimary().get();
            if (primary instanceof AQuantPrimaryExpr) {
                return Optional.of((AQuantPrimaryExpr)primary);
            }
        }
        return Optional.empty();
    }

    public static PPrimaryExpr toPrimaryExpr(PExpr expr) {
        IsPrimary p = new IsPrimary();
        expr.apply(p);
        return p.getPrimary().orElse(new AQuantPrimaryExpr(null, new TLPar(), expr, new TRPar()));
    }

    public static PPrimaryExpr defaultPrimaryExpr() {
        return new ALiteralPrimaryExpr(new ANumericLiteral(new TNumericLiteral("0")));
    }

    public static PExpr toExpr(Node node) {
        if (node instanceof PSumExpr) {
            PSumExpr sumExpr = (PSumExpr) node;
            return toExpr(new AArithExpr(sumExpr));
        } else if (node instanceof PArithExpr) {
            PArithExpr arithExpr = (PArithExpr) node;
            return toExpr(new AArithExprBoolExpr(arithExpr));
        } else if (node instanceof PBoolExpr) {
            PBoolExpr boolExpr = (PBoolExpr) node;
            return toExpr(new ABoolAssignExpr(boolExpr));
        } else if (node instanceof PAssignExpr) {
            PAssignExpr assignExpr = (PAssignExpr) node;
            return toExpr(new AAssignExprLogicExpr(assignExpr));
        } else if (node instanceof PLogicExpr) {
            PLogicExpr logicExpr = (PLogicExpr) node;
            return toExpr(logicExpr);
        } else if (node instanceof PPrimaryExpr) {
            return toExpr((PPrimaryExpr)node);
        } else {
            throw new RuntimeException("Unable to convert node of type " + node.getClass() + " to expr");
        }
    }
    public static PExpr toExpr(PLogicExpr expr) {
        return new AExpr(expr);
    }
    public static PExpr toExpr(PPrimaryExpr expr) {
        return toExpr(toLogicExpr(expr));
    }
    public static PExpr toExpr(PNewExpr expr) {
        return toExpr(new ANewExprPrimaryExpr(expr));
    }
    public static PExpr defaultExpr() {
        return toExpr(defaultPrimaryExpr());
    }
    public static PLogicExpr toLogicExpr(PPrimaryExpr expr) {
        return toLogicExpr(toAssignExpr(expr));
    }
    public static PLogicExpr toLogicExpr(PExpr expr) {
        return toLogicExpr(toPrimaryExpr(expr));
    }
    public static PAssignExpr toAssignExpr(PPrimaryExpr expr) {
        return new ABoolAssignExpr(new AArithExprBoolExpr(new AArithExpr(new AProductSumExpr(new APrimaryExprProductExpr(expr)))));
    }
    public static PLogicExpr toLogicExpr(PAssignExpr expr) {
        return new AAssignExprLogicExpr(expr);
    }
    public static PBoolExpr toBoolExpr(PLiteral literal) {
        return toBoolExpr(new ALiteralPrimaryExpr(literal));
    }
    public static PBoolExpr toBoolExpr(PPrimaryExpr primary) {
        return toBoolExpr(new AArithExpr(new AProductSumExpr(new APrimaryExprProductExpr(primary))));
    }
    public static PBoolExpr toBoolExpr(PArithExpr arithExpr) {
        return new AArithExprBoolExpr(arithExpr);
    }
    public static PBoolExpr toBoolExpr(PExpr expr) {
        return toBoolExpr(toPrimaryExpr(expr));
    }

    public static <T> Optional<T> getParent(Node n, Class<T> cls) {

        while(n != null) {
            if (cls.isAssignableFrom(n.getClass())) {
                return Optional.of(cls.cast(n));
            }
            n = n.parent();
        }
        return Optional.empty();
    }

    public static PAssignExpr assignExpr(String id, PPrimaryExpr expr) {
        PAssignValue val = new AAssignAssignValue(new TEqOp(), new AArithExprBoolExpr(new AArithExpr(new AProductSumExpr(new APrimaryExprProductExpr(expr)))));
        return new AAssignAssignExpr(new TIdentifier(id), val);
    }

    public static PLogicExpr toSequence(List<PAssignExpr> exprs, int startIndex) {

        int size = exprs.size() - startIndex;
        if (size == 0) {
            return toLogicExpr(defaultPrimaryExpr());
        } else if (size == 1) {
            return toLogicExpr(exprs.get(startIndex));
        } else {
            PAssignExpr hd = exprs.get(startIndex);
            PLogicExpr tlExpr = toSequence(exprs, startIndex + 1);
            return new ASequenceLogicExpr(hd, new TSemicolon(), tlExpr);
        }
    }

    public static PExpr toSequence(List<PAssignExpr> exprs) {
        return toExpr(toSequence(exprs, 0));
    }

    public static ANewParams toNewParams(List<ANewParam> ls) {
        List<ANewParamsTl> lsTl = new ArrayList<>();
        ANewParam hd = null;
        if (ls.size() >= 1) {
            hd = ls.get(0);
        }
        for(int i = 1; i < ls.size(); i++) {
            lsTl.add(new ANewParamsTl(new TComma(), ls.get(i)));
        }
        return new ANewParams(hd, lsTl);
    }

    public static Optional<TStatic> getStaticModifier(AFuncDecl node) {
        for (Object curModObj : node.getFuncModifier()) {
            if (curModObj instanceof AStaticFuncModifier) {
                AStaticFuncModifier curMod = (AStaticFuncModifier)curModObj;
                TStatic tStatic = curMod.getStatic();
                return Optional.of(tStatic);
            }
        }
        return Optional.empty();
    }

    public static Optional<TPure> getPureModifier(AFuncDecl node) {
        for (Object curModObj : node.getFuncModifier()) {
            if (curModObj instanceof APureFuncModifier) {
                APureFuncModifier curMod = (APureFuncModifier)curModObj;
                TPure tPure = curMod.getPure();
                return Optional.of(tPure);
            }
        }
        return Optional.empty();
    }

    public static boolean isPure(AFuncDecl node) {
        return getPureModifier(node).isPresent();
    }

    public static boolean isStatic(AFuncDecl node) {
        return getStaticModifier(node).isPresent() ;
    }

    public static boolean isZero(PBoundsExpr expr) {
        return Util.equals(expr, Util.zeroBoundsExpr());
    }

    public static boolean equals(Node a, Node b) {

        if (a == b) {
            return true;
        } else if (a == null || b == null) {
            return true;
        } else {
            return a.toString().equals(b.toString());
        }
    }

    public static PBoundsExpr toBoundsExpr(PPrimaryBoundsExpr primary) {
        return new ABoundsExpr(toSumBoundsExpr(primary));
    }

    public static PBoundsExpr zeroBoundsExpr() {
        return toBoundsExpr(new ALiteralPrimaryBoundsExpr(new ANumericLiteral(new TNumericLiteral("0"))));
    }

    public static PBoundsExpr addBounds(PBoundsExpr e1, PBoundsExpr e2) {
        return new ABoundsExpr(new ABinOpSumBoundsExpr(toProductBoundsExpr(e1), new TSumOp("+"), toSumBoundsExpr(e2)));
    }
    public static PBoundsExpr subtractBounds(PBoundsExpr e1, PBoundsExpr e2) {
        return new ABoundsExpr(new ABinOpSumBoundsExpr(toProductBoundsExpr(e1), new TSumOp("-"), toSumBoundsExpr(e2)));
    }

    public static Optional<AFuncExpr> getFuncExpr(PExpr expr) {
        IsPrimary p = new IsPrimary();
        expr.apply(p);
        if (p.getPrimary().isPresent()) {
            PPrimaryExpr primary = p.getPrimary().get();
            if (primary instanceof AFuncExprPrimaryExpr) {
                AFuncExprPrimaryExpr funcPrimary = (AFuncExprPrimaryExpr)primary;
                return Optional.of((AFuncExpr)funcPrimary.getFuncExpr());
            }
        }
        return Optional.empty();
    }

    public static PBoundsExpr multiplyBounds(PBoundsExpr e1, PBoundsExpr e2) {
        return new ABoundsExpr(new AProductBoundsExprSumBoundsExpr(new ABinOpProductBoundsExpr(toExponentBoundsExpr(e1), new TProductOp("*"), toProductBoundsExpr(e2))));
    }

    public static PPrimaryBoundsExpr toPrimaryBoundsExpr(PBoundsExpr e) {
        return new AQuantPrimaryBoundsExpr(new TLPar(), e, new TRPar());
    }

    public static PProductBoundsExpr toProductBoundsExpr(PBoundsExpr e) {
        return toProductBoundsExpr(toPrimaryBoundsExpr(e));
    }

    public static PProductBoundsExpr toProductBoundsExpr(PPrimaryBoundsExpr e) {
        return new AExponentBoundsExprProductBoundsExpr(toExponentBoundsExpr(e));
    }

    public static PExponentBoundsExpr toExponentBoundsExpr(PBoundsExpr e) {
        return toExponentBoundsExpr(toPrimaryBoundsExpr(e));
    }
    public static PExponentBoundsExpr toExponentBoundsExpr(PPrimaryBoundsExpr e) {
        return new APrimaryBoundsExprExponentBoundsExpr(e);
    }

    public static PSumBoundsExpr toSumBoundsExpr(PPrimaryBoundsExpr e) {
        return new AProductBoundsExprSumBoundsExpr(toProductBoundsExpr(e));
    }

    public static PSumBoundsExpr toSumBoundsExpr(PBoundsExpr e) {
        return toSumBoundsExpr(toPrimaryBoundsExpr(e));
    }

    public static PProp negateProp(PProp prop) {
        return new AProp(new ALogicForallExistsProp(new ANotLogicProp(new TBang(), new AArithBoolProp(new AArithProp(new AProductSumProp(new APrimaryProductProp(new AQuantPrimaryProp(new TLPar(), prop, new TRPar()))))))));
    }

    public static Type functionReturnType(AFuncDecl funcDecl) {
        Type type = Type.Opaque;
        if (Util.isPure(funcDecl)) {
            type = Type.Bitstring;
        }

        if (funcDecl.getTypeSuffix() != null) {
            IdentifierAndTypeExtractor ext = new IdentifierAndTypeExtractor();
            funcDecl.getTypeSuffix().apply(ext);
            type = ext.getType();
        }

        return type;
    }

    public static List<Type> functionParameterTypes(AFuncDecl funcDecl) {
        Type defaultType = Type.Opaque;
        if (Util.isPure(funcDecl)) {
            defaultType = Type.Bitstring;
        }

        FormalParamsCollector col = new FormalParamsCollector();
        funcDecl.getFormalParamsList().apply(col);
        List<Type> result = col.getTypes();

        for(int i = 0; i < result.size(); i++) {
            Type curType = result.get(i);
            if(curType == null) {
                result.set(i, defaultType);
            }
        }

        return result;
    }

}
