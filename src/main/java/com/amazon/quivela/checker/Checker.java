/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker;

import com.amazon.quivela.checker.execution.ProofTaskQueue;
import com.amazon.quivela.checker.tactic.*;
import com.amazon.quivela.checker.tactic.boogie.BoogieFunctions;
import com.amazon.quivela.checker.visitor.*;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.lexer.Lexer;
import com.amazon.quivela.parser.lexer.LexerException;
import com.amazon.quivela.parser.node.*;
import com.amazon.quivela.parser.parser.Parser;
import com.amazon.quivela.parser.parser.ParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.util.*;

public class Checker extends DepthFirstAdapter {

    private int line = 0;
    private int pos = 0;

    private ProofTaskQueue taskQueue = new ProofTaskQueue();
    Deque<Type> defaultType = new ArrayDeque();
    Deque<File> checkFile = new ArrayDeque();
    public Set<String> importedModules = new HashSet();

    public Checker(File f) {
        checkFile.push(f);
        symbolTable.pushFrame(true);
    }

    public static void check(File f, PDevelopment dev) throws CheckException {
        Checker checker = new Checker(f);
        dev.apply(checker);
        checker.checkTasks();
    }

    public void checkTasks() throws CheckException {
        taskQueue.checkTasks();
    }

    SymbolTable symbolTable = new SymbolTable();
    SymbolTableFrame letDecls = null;

    BoogieFunctions functions = new BoogieFunctions();
    Map<String, ANewExpr> classes = new HashMap();
    List<AAxiomDecl> axioms = new ArrayList();

    public static class Equiv {
        private final PExpr left;
        private final PExpr right;
        private final Collection<String> vars;
        private final PBoundsExpr distance;

        public Equiv(PExpr left, PExpr right, Collection<String> vars, PBoundsExpr distance) {
            this.left = left;
            this.right = right;
            this.vars = vars;
            this.distance = distance;
        }

        public PExpr getLeft() {
            return left;
        }
        public PExpr getRight() {
            return right;
        }
        public PBoundsExpr getDistance() { return distance; }
    }

    private static abstract class ProofObligation {

    }

    private static class ProofObligationEquiv extends ProofObligation {

        private final Equiv equiv;
        public PExpr leftExpr;
        public PExpr rightExpr;
        public PBoundsExpr distance = Util.zeroBoundsExpr();

        public ProofObligationEquiv(Equiv equiv) {
            this.equiv = equiv;

            this.leftExpr = (PExpr)equiv.getLeft().clone();
            this.rightExpr = (PExpr)equiv.getRight().clone();
        }

        public Equiv getEquiv() {
            return equiv;
        }
    }

    private static class ProofObligationEquivBuilder extends ProofObligation {
        private PExpr leftExpr = null;
        private PExpr rightExpr = null;
        private Collection<String> vars = null;
        private PBoundsExpr distance = Util.zeroBoundsExpr();

        public ProofObligationEquivBuilder setLeft(PExpr left) {
            this.leftExpr = (PExpr)left.clone();
            return this;
        }

        public ProofObligationEquivBuilder setRight(PExpr right) {
            this.rightExpr = (PExpr)right.clone();
            return this;
        }

        public PExpr getRight() {
            return rightExpr;
        }

        public ProofObligationEquivBuilder setDistance(PBoundsExpr bounds) {
            this.distance = (PBoundsExpr)bounds.clone();
            return this;
        }

        public ProofObligationEquivBuilder setVars(Collection<String> vars) {
            this.vars = vars;
            return this;
        }

        ProofObligationEquiv build() {
            return new ProofObligationEquiv(new Equiv(leftExpr, rightExpr, vars, distance));
        }

    }

    private static final class IdentifiedEquiv {
        private final String id;
        private final Equiv equiv;

        public IdentifiedEquiv(String id, Equiv equiv) {
            this.id = id;
            this.equiv = equiv;
        }

        public String getId() {
            return id;
        }
        public Equiv getEquiv() {
            return equiv;
        }
    }

    public Map<String, Equiv> theorems = new HashMap();
    private Deque<ProofObligation> proofObligations = new ArrayDeque();
    private Deque<IdentifiedEquiv> pendingEquivs = new ArrayDeque();

    @Override
    public void outAConstDecl(AConstDecl node)
    {
        IdentifierAndTypeExtractor ext = new IdentifierAndTypeExtractor((node.getIdentifierAndType()));
        TIdentifier idToken = ext.getIdentifierToken();
        String id = idToken.getText();

        // constants may not be redeclared
        if (!symbolTable.varDeclarationAllowed(id)) {
            handleCheckException(new CheckException(checkFile.peek(), idToken.getLine(), idToken.getPos(), id + " already declared."));
        }

        Type type = ext.getType();
        if (type == null) {
            type = Type.Bitstring;
        }

        symbolTable.addSymbol(id, type);
    }

    @Override
    public void inANewExpr(ANewExpr newExpr) {
        symbolTable.pushFrame(false);
    }

    @Override
    public void outANewExpr(ANewExpr newExpr) {
        symbolTable.popFrame();

        if (newExpr.getClassIdent() != null) {
            AClassIdent ident = (AClassIdent)newExpr.getClassIdent();
            classes.put(ident.getIdentifier().getText(), newExpr);
        }
    }

    @Override
    public void inAMethodDef(AMethodDef method) {
        symbolTable.pushFrame(false);

        LocalVariableDeclCollector col = new LocalVariableDeclCollector(symbolTable);

        method.getFuncBody().apply(col);

        defaultType.push(Type.Bitstring);
    }

    @Override
    public void outAMethodDef(AMethodDef method) {

        defaultType.pop();
        symbolTable.popFrame();
    }

    @Override
    public void inANewParam(ANewParam newParam) {
        symbolTable.pushFrame(false);
        LocalVariableDeclCollector col = new LocalVariableDeclCollector(symbolTable);
        newParam.getExpr().apply(col);
    }

    @Override
    public void outANewParam(ANewParam newParam) {

        symbolTable.popFrame();

        TIdentifier idToken = newParam.getIdentifier();
        String id = idToken.getText();
        // each member may only be assigned once
        if (!symbolTable.varDeclarationAllowed(id)) {
            handleCheckException(new CheckException(checkFile.peek(), idToken.getLine(), idToken.getPos(), id + " already defined."));
        }
        symbolTable.addSymbol(id, Type.Opaque);
    }

    @Override
    public void outAFormalParam(AFormalParam formalParam) {
        IdentifierAndTypeExtractor ext = new IdentifierAndTypeExtractor(formalParam.getIdentifierAndType());
        TIdentifier idToken = ext.getIdentifierToken();
        String id = idToken.getText();
        // each parameter may only be declared once
        if (!symbolTable.varDeclarationAllowed(id)) {
            handleCheckException(new CheckException(checkFile.peek(), idToken.getLine(), idToken.getPos(), id + " already declared."));
        }

        Type type = ext.getType();
        if (type == null) {
            type = defaultType.element();
        }
        symbolTable.addSymbol(id, type);
    }



    @Override
    public void outAAssignAssignExpr(AAssignAssignExpr assign) {
        TIdentifier idToken = assign.getIdentifier();
        String id = idToken.getText();
        symbolTable.addSymbol(id, Type.Opaque);
    }

    @Override
    public void inALookupPrimaryExpr(ALookupPrimaryExpr lookup) {
        TIdentifier idToken = lookup.getIdentifier();
        String id = idToken.getText();
        if (!symbolTable.varReferenceAllowed(id)) {
            handleCheckException(new CheckException(checkFile.peek(), idToken.getLine(), idToken.getPos(), id + " not declared."));
        }
    }

    private boolean isPure(PFuncBody body) {
        return false;
    }

    @Override
    public void inAFuncDecl(AFuncDecl node)
    {
        Optional<TPure> tPure = Util.getPureModifier(node);
        if (tPure.isPresent()) {
            if (node.getFuncBody() != null) {
                if (!isPure(node.getFuncBody())) {
                    handleCheckException(new CheckException(checkFile.peek(), tPure.get().getLine(), tPure.get().getPos(), "Function body is not pure."));
                }
            }

            defaultType.push(Type.Bitstring);

        } else {
            defaultType.push(Type.Opaque);
        }

        symbolTable.pushFrame(false);

    }

    @Override
    public void outAFuncDecl(AFuncDecl node)
    {
        symbolTable.popFrame();
        defaultType.pop();
        functions.put(node.getIdentifier().getText(), node);
    }

    @Override
    public void outAFuncExpr(AFuncExpr node) {
        String funcName = node.getIdentifier().getText();
        if (functions.getByName(funcName) == null) {
            handleCheckException(new CheckException(checkFile.peek(), node.getIdentifier(), "Function not declared: " + funcName));
        }
    }

    @Override
    public void outAAxiomDecl(AAxiomDecl node)
    {
        axioms.add(node);
    }

    @Override
    public void inAForallExistsForallExistsProp(AForallExistsForallExistsProp node)
    {
        symbolTable.pushFrame(true);
        defaultType.push(Type.Bitstring);
    }

    @Override
    public void outAForallExistsForallExistsProp(AForallExistsForallExistsProp node)
    {
        defaultType.pop();
        symbolTable.popFrame();
    }

    @Override
    public void caseAFactDecl(AFactDecl node) {
        symbolTable.pushLogicalFrame();
        defaultType.push(Type.Opaque);
        node.getFormalParamsList().apply(this);
        defaultType.pop();

        String id = node.getIdentifier().getText().trim();
        if (theorems.get(id) != null) {
            handleCheckException(new CheckException(checkFile.peek(), node.getIdentifier(), "theorem identifier already declared: " + id));
        }
        SymbolTableFrame varsFrame = symbolTable.peekFrame();
        Equiv factEquiv = new Equiv((PExpr)node.getLeftExpr().clone(), (PExpr)node.getRightExpr().clone(), varsFrame.getIds(), (PBoundsExpr)node.getBoundsExpr().clone());
        pendingEquivs.push(new IdentifiedEquiv(id, factEquiv));
    }

    private ProofObligationEquiv popEquivObligation(int line, int pos) {
        ProofObligationEquiv result = peekEquivObligation(line, pos);
        proofObligations.pop();
        return result;
    }

    ProofObligationEquivBuilder peekEquivBuilderObligation(int line, int pos) {
        ProofObligation ob = proofObligations.peek();
        if (ob instanceof ProofObligationEquivBuilder) {
            return (ProofObligationEquivBuilder)ob;
        } else {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, "Not an equivalence builder obligation"));
        }
        return null;
    }

    ProofObligationEquiv peekEquivObligation(int line, int pos) {
        ProofObligation ob = proofObligations.peek();
        if (ob instanceof ProofObligationEquiv) {
            return (ProofObligationEquiv)ob;
        } else {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, "Not an equivalence obligation"));
        }
        return null;
    }

    @Override
    public void caseATheorem(ATheorem node) {

        node.getFactDecl().apply(this);

        Equiv factEquiv = pendingEquivs.peek().getEquiv();
        proofObligations.push(new ProofObligationEquiv(factEquiv));

        node.getProof().apply(this);

        // proof was checked when it was closed, but the proof obligation was left on the stack
        proofObligations.pop();
        if (!proofObligations.isEmpty()) {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, "Proof obligations remain."));
        }
        IdentifiedEquiv iEquiv = pendingEquivs.pop();
        symbolTable.popFrame();
        theorems.put(iEquiv.getId(), iEquiv.getEquiv());
    }

    private static class ModuleFilenameConverter extends DepthFirstAdapter {

        List<String> ids = new ArrayList();

        @Override
        public void caseTIdentifier(TIdentifier id) {
            ids.add(id.getText().trim());
        }

        public String getFilePath() {
            StringBuffer result = new StringBuffer();
            result.append(ids.get(0));
            for (int i = 1; i < ids.size(); i++) {
                result.append(File.separator + ids.get(i));
            }
            result.append(".qvl");
            return result.toString();
        }

        public String getModuleName() {
            StringBuffer result = new StringBuffer();
            result.append(ids.get(0));
            for (int i = 1; i < ids.size(); i++) {
                result.append("." + ids.get(i));
            }
            return result.toString();
        }
    }

    @Override
    public void outAImportStmt(AImportStmt node) {

        ModuleFilenameConverter conv = new ModuleFilenameConverter();
        node.getModuleIdentifier().apply(conv);

        if (!importedModules.contains(conv.getModuleName())) {

            importedModules.add(conv.getModuleName());

            String filename = conv.getFilePath();
            String filePath = filename;

            try {
                File f = new File(filePath);
                checkFile.push(f);
                Parser p = new Parser(new Lexer(new PushbackReader(new FileReader(filePath), 1024)));
                Start tree = p.parse();
                // This behaves like an include, because it will use the current symbol table, etc.
                // TODO: make this more module-like
                tree.getPDevelopment().apply(this);
                checkFile.pop();
            } catch (IOException ex) {
                handleCheckException(new CheckException(checkFile.peek(), node.getImport(), "Unable to read file: " + filePath));
            } catch (LexerException | ParserException ex) {
                handleCheckException(new CheckException(ex));
            }
        }
    }

    @Override
    public void outAProof(AProof node) {

        line = node.getRCurl().getLine();
        pos = node.getLCurl().getPos();

        // leave proof obligation on the stack. It will be removed by subgoal/theorem handler
        ProofObligationEquiv ob = peekEquivObligation(line, pos);

        checkEquiv(ob.leftExpr, ob.rightExpr);
        checkBounds(ob.distance, ob.getEquiv().getDistance());
    }

    @Override
    public void outAAssumption(AAssumption node) {

        symbolTable.popFrame();

        IdentifiedEquiv iEquiv = pendingEquivs.pop();
        theorems.put(iEquiv.getId(), iEquiv.getEquiv());

    }

    private static class TopNewExprCollector extends AnalysisAdapter {

        private ANewExpr newExpr = null;

        @Override
        public void caseAExpr(AExpr expr) {
            expr.getLogicExpr().apply(this);
        }

        @Override
        public void caseAAssignExprLogicExpr(AAssignExprLogicExpr expr) {
            expr.getAssignExpr().apply(this);
        }
        @Override
        public void caseABoolAssignExpr(ABoolAssignExpr expr) {
            expr.getBoolExpr().apply(this);
        }
        @Override
        public void caseAArithExprBoolExpr(AArithExprBoolExpr expr) {
            expr.getArithExpr().apply(this);
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
        public void caseAPrimaryExprProductExpr(APrimaryExprProductExpr expr) {
            expr.getPrimaryExpr().apply(this);
        }


        @Override
        public void caseANewExprPrimaryExpr(ANewExprPrimaryExpr expr) {
            expr.getNewExpr().apply(this);
        }

        @Override
        public void caseANewExpr(ANewExpr expr) {
            newExpr = expr;
        }

        public ANewExpr getNewExpr() {
            return newExpr;
        }
    }

    @Override
    public void inASubgoalTactic(ASubgoalTactic node){

        line = node.getTwiddle().getLine();
        pos = node.getTwiddle().getPos();

        ProofObligationEquivBuilder newOb = new ProofObligationEquivBuilder();
        ProofObligationEquiv topOb = peekEquivObligation(line, pos);
        // push a new proof obligation for the subgoal onto the stack
        // The subgoal initially has the same expr on the left and right
        // the analysis will modify the right expr as it visits the nodes
        newOb.setLeft(topOb.leftExpr);
        newOb.setRight(topOb.leftExpr);
        // variables are introduced by the theorem statement, so they cannot be changed by subgoals
        newOb.setVars(topOb.getEquiv().vars);
        newOb.setDistance(node.getBoundsExpr());
        proofObligations.push(newOb);
    }

    @Override
    public void outASubgoalTactic(ASubgoalTactic node) {
        // the proof has been checked by the handler for AProof
        // But it was left on the stack, so it can be used here to update the state
        ProofObligationEquiv subgoalOb = peekEquivObligation(line, pos);
        proofObligations.pop();
        ProofObligationEquiv topOb = peekEquivObligation(line, pos);
        topOb.leftExpr = subgoalOb.getEquiv().getRight();
        topOb.distance = Util.addBounds(topOb.distance, node.getBoundsExpr());
    }

    @Override
    public void inANewRewriteSubgoalExpr(ANewRewriteSubgoalExpr rewrite) {

        ProofObligationEquivBuilder topOb = peekEquivBuilderObligation(line, pos);
        PExpr leftExpr = topOb.leftExpr;
        topOb.setLeft(leftExpr);

        // need constructor symbols for the rest of the rewrite
        TopNewExprCollector col = new TopNewExprCollector();
        leftExpr.apply(col);
        ANewExpr newExpr = col.newExpr;
        if (newExpr == null) {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, "Not a new expression: " + leftExpr.toString()));
        }

        NewParamsCollector paramsCol = new NewParamsCollector();
        newExpr.getNewParamsList().apply(paramsCol);
        symbolTable.pushFrame(false);
        for(ANewParam  curparam : paramsCol.getParams()) {
            String id = curparam.getIdentifier().getText().trim();
            if(!symbolTable.varDeclarationAllowed(id)) {
                handleCheckException(new CheckException(checkFile.peek(), line, pos, "Already declared: " + id));
            }
            symbolTable.addSymbol(id, Type.Opaque);
        }
    }

    @Override
    public void outANewRewriteSubgoalExpr(ANewRewriteSubgoalExpr rewrite) {
        symbolTable.popFrame();

        ProofObligationEquivBuilder topOb = peekEquivBuilderObligation(line, pos);
        proofObligations.pop();
        proofObligations.push(topOb.build());
    }

    @Override
    public void outAExprSubgoalExpr(AExprSubgoalExpr expr) {
        ProofObligationEquivBuilder topOb = peekEquivBuilderObligation(line, pos);
        topOb.setRight(expr.getExpr());
        proofObligations.pop();

        proofObligations.push(topOb.build());
    }

    public static class NewConstructorRewriter extends AnalysisAdapter {

        private final PNewParamsList newParams;

        public NewConstructorRewriter(ANewNewRewriteTerm rewrite) {
            this.newParams = rewrite.getNewParamsList();
        }

        @Override
        public void caseANewParamsList(ANewParamsList ls) {
            ls.replaceBy(newParams);
        }
    }

    public static class PreviousExprRewriter extends DepthFirstAdapter {

        private PExpr prevExpr;

        PreviousExprRewriter(PExpr prevExpr) {
            this.prevExpr = prevExpr;
        }

        @Override
        public void outADotdotdotPrimaryExpr(ADotdotdotPrimaryExpr node) {
            node.replaceBy(Util.toPrimaryExpr((PExpr)prevExpr.clone()));
        }
    }

    public static class NewMethodRewriter extends AnalysisAdapter {

        private final AMethodDef method;

        public NewMethodRewriter(AMethodNewRewriteTerm rewrite) {
            method = (AMethodDef)rewrite.getMethodDef();
        }

        public NewMethodRewriter(AMethodDef method) {
            this.method = method;
        }

        @Override
        public void caseAMethodList(AMethodList node) {
            for(Object curMethodObj : node.getMethodDef()) {
                PMethodDef curMethod = (PMethodDef)curMethodObj;
                curMethod.apply(this);
            }
        }

        @Override
        public void caseAMethodDef(AMethodDef def) {
            if (def.getIdentifier().getText().trim().equals(
                    method.getIdentifier().getText().trim())) {

                PMethodDef newMethod = (PMethodDef)method.clone();
                PExpr bodyExpr = ((AFuncBody)def.getFuncBody()).getExpr();
                PreviousExprRewriter prevRewriter = new PreviousExprRewriter(bodyExpr);
                newMethod.apply(prevRewriter);
                def.replaceBy(newMethod);
            }
        }
    }

    @Override
    public void inANewNewRewriteTerm(ANewNewRewriteTerm rewrite) {

        // replace the symbol table frame containing class fields
        symbolTable.popFrame();
        symbolTable.pushFrame(false);
        // the NewParams case will create and populate a new symbol table frame
    }

    @Override
    public void outANewNewRewriteTerm(ANewNewRewriteTerm rewrite) {

        ProofObligationEquivBuilder topOb = peekEquivBuilderObligation(line, pos);
        Optional<ANewExpr> newExprOpt = Util.toNewExpr(topOb.getRight());
        if (newExprOpt.isPresent()) {
            ANewExpr newExpr = (ANewExpr)newExprOpt.get().clone();
            newExpr.getNewParamsList().apply(new NewConstructorRewriter(rewrite));
            topOb.setRight(Util.toExpr(newExpr));
        } else {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, "Not a new expression: " + topOb.getRight().toString()));
        }
    }

    @Override
    public void outAMethodNewRewriteTerm(AMethodNewRewriteTerm rewrite) {

        ProofObligationEquivBuilder topOb = peekEquivBuilderObligation(line, pos);
        Optional<ANewExpr> newExprOpt = Util.toNewExpr(topOb.getRight());
        if (newExprOpt.isPresent()) {
            ANewExpr newExpr = (ANewExpr)newExprOpt.get().clone();
            newExpr.getMethodList().apply(new NewMethodRewriter(rewrite));
            topOb.setRight(Util.toExpr(newExpr));
        } else {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, "Not a new expression: " + topOb.getRight().toString()));
        }

    }

    @Override
    public void outAUnfoldTactic(AUnfoldTactic node) {

        List<String> ids = null;
        PIdentifierList idList = node.getIdentifierList();
        if (idList != null) {
            IdentifierCollector idCol = new IdentifierCollector();
            idList.apply(idCol);
            ids = new ArrayList<String>();
            ids.addAll(idCol.getIds());
        }

        Unfold unfold = new Unfold(checkFile.peek(), taskQueue, symbolTable, functions);
        PExpr leftExpr = peekEquivObligation(node.getUnfold().getLine(), node.getUnfold().getPos()).leftExpr;
        try {
            unfold.transform(ids, leftExpr, node.getUnfold().getLine(), node.getUnfold().getPos());
        } catch(CheckException ex) {
            handleCheckException(ex);
        }
    }

    @Override
    public void outAInlineTactic(AInlineTactic node) {

        List<String> ids = new ArrayList();
        PIdentifierList idList = node.getIdentifierList();
        if (idList != null) {
            IdentifierCollector idCol = new IdentifierCollector();
            idList.apply(idCol);
            ids.addAll(idCol.getIds());
        }

        Inline inline = new Inline();
        PExpr leftExpr = peekEquivObligation(line, pos).leftExpr;
        try {
            inline.transform(checkFile.peek(), leftExpr, symbolTable, ids);
        } catch(CheckException ex) {
            handleCheckException(ex);
        }

    }

    @Override
    public void outASymmetryTactic(ASymmetryTactic node) {

        ProofObligationEquiv topOb = peekEquivObligation(node.getSymmetry().getLine(), node.getSymmetry().getPos());
        PExpr tmp = topOb.leftExpr;
        topOb.leftExpr = topOb.rightExpr;
        topOb.rightExpr = tmp;
    }

    @Override
    public void outAAdmitTactic(AAdmitTactic node) {

        ProofObligationEquiv topOb = peekEquivObligation(node.getAdmit().getLine(), node.getAdmit().getPos());

        topOb.leftExpr = (PExpr)topOb.rightExpr.clone();
    }

    @Override
    public void outATrivialTactic(ATrivialTactic node) {

        ProofObligationEquiv topOb = peekEquivObligation(node.getTrivial().getLine(), node.getTrivial().getPos());

        checkEquiv(topOb.leftExpr, topOb.rightExpr);
        topOb.leftExpr = (PExpr)topOb.rightExpr.clone();

    }

    @Override
    public void inAAutoTactic(AAutoTactic node) {
        symbolTable.pushFrame(true);
    }

    @Override
    public void outAAutoTactic(AAutoTactic node) {

        Auto auto = new Auto(taskQueue, symbolTable, functions, axioms, classes);
        ProofObligationEquiv topOb = peekEquivObligation(node.getAuto().getLine(), node.getAuto().getPos());

        try {
            auto.check(checkFile.peek(), line, pos, topOb.leftExpr, topOb.rightExpr, "Checking auto");
            topOb.leftExpr = (PExpr)topOb.rightExpr.clone();;
        } catch (CheckException ex) {
            handleCheckException(ex);
        }

        symbolTable.popFrame();
    }

    @Override
    public void inABisimTactic(ABisimTactic node) {
        symbolTable.pushFrame(true);
    }

    @Override
    public void outABisimTactic(ABisimTactic node) {

        Bisim bisim = new Bisim(taskQueue, symbolTable, functions, axioms, classes);
        ProofObligationEquiv topOb = peekEquivObligation(node.getBisim().getLine(), node.getBisim().getPos());

        try {
            PExpr goalExpr = topOb.rightExpr;
            Optional<ANewExpr> leftNewOpt = Util.toNewExpr(topOb.leftExpr);
            Optional<ANewExpr> goalNewOpt = Util.toNewExpr(goalExpr);

            if (!leftNewOpt.isPresent()) {
                handleCheckException(new CheckException(checkFile.peek(), line, pos, "not a new expression: " + topOb.leftExpr));
            }
            if (!goalNewOpt.isPresent()) {
                handleCheckException(new CheckException(checkFile.peek(), line, pos, "not a new expression: " + goalExpr));
            }

            List<ABisimProp> props = null;
            if (node.getBisimProps() != null) {
                BisimPropsCollector col = new BisimPropsCollector();
                node.getBisimProps().apply(col);
                props = col.getProps();
            }
            bisim.check(checkFile.peek(), node.getBisim().getLine(), node.getBisim().getPos(), leftNewOpt.get(), goalNewOpt.get(), props);
            topOb.leftExpr = (PExpr)goalExpr.clone();
        } catch (CheckException ex) {
            handleCheckException(ex);
        }

        symbolTable.popFrame();
    }

    @Override
    public void outARewriteTactic(ARewriteTactic node) {
        TIdentifier factIdToken = node.getIdentifier();
        String factId = factIdToken.getText().trim();
        Equiv equiv = theorems.get(factId);
        if (equiv != null) {
            ProofObligationEquiv topOb = peekEquivObligation(node.getRewrite().getLine(), node.getRewrite().getPos());

            Rewriter rewriter = new Rewriter(equiv.left, equiv.vars, equiv.right, equiv.distance, Util.isZero(equiv.distance), topOb.leftExpr);
            topOb.leftExpr.apply(rewriter);
            if (rewriter.getRewrites().isEmpty()) {
                handleCheckException(new CheckException(checkFile.peek(), factIdToken, "Nothing to rewrite"));
            }

            for (Rewriter.Rewrite curRewrite : rewriter.getRewrites()) {
                topOb.distance = Util.addBounds(topOb.distance, curRewrite.distance);
            }

            Optional<PExpr> topExpr = rewriter.getTopExpr();
            if (topExpr.isPresent()) {
                topOb.leftExpr = topExpr.get();
            }
        } else {
            handleCheckException(new CheckException(checkFile.peek(), factIdToken, "Undeclared symbol: " + factId));
        }
    }

    @Override
    public void outAHybridTactic(AHybridTactic node) {

        TIdentifier factIdToken = node.getStepFact();
        String factId = factIdToken.getText().trim();
        Equiv equiv = theorems.get(factId);
        if (equiv != null) {

            // check that the fact has the appropriate form
            // 1) quantified over a single variable x
            String equivVar=null;
            for(String curVar : equiv.vars) {
                if (equivVar != null) {
                    handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "Hybrid argument facts must take a single parameter"));
                }
                equivVar = curVar;
            }
            if (equivVar == null) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "Hybrid argument facts must take a single parameter"));
            }
            // 2) LHS is a function with actual parameter x
            Optional<AFuncExpr> leftFuncOpt = Util.getFuncExpr(equiv.getLeft());
            if (!leftFuncOpt.isPresent()) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "A hybrid argument fact expressions must be a function call with a single parameter."));
            }
            AFuncExpr leftFunc = leftFuncOpt.get();
            ActualParamsCollector leftParamsColl = new ActualParamsCollector();
            leftFunc.getActualParams().apply(leftParamsColl);
            if (leftParamsColl.getExprs().size() != 1) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "A hybrid argument fact expressions must be a function call with a single parameter."));
            }
            PExpr leftParam = leftParamsColl.getExprs().get(0);
            PPrimaryExpr lookupExpr = new ALookupPrimaryExpr(new TIdentifier(equivVar));
            Node expectedLeftParam = lookupExpr;
            if (!Util.equals(leftParam, expectedLeftParam)) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "The parameter of the left hybrid fact expression must be " + expectedLeftParam));
            }

            // 3) RHS is a function with actual parameter x+1
            Optional<AFuncExpr> rightFuncOpt = Util.getFuncExpr(equiv.getRight());
            if (!rightFuncOpt.isPresent()) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "A hybrid argument fact expressions must be a function with a single parameter."));
            }
            AFuncExpr rightFunc = rightFuncOpt.get();
            ActualParamsCollector rightParamsColl = new ActualParamsCollector();
            rightFunc.getActualParams().apply(rightParamsColl);
            if (rightParamsColl.getExprs().size() != 1) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "A hybrid argument fact expressions must be a function with a single parameter."));
            }
            PExpr rightParam = rightParamsColl.getExprs().get(0);
            PPrimaryExpr one = new ALiteralPrimaryExpr(new ANumericLiteral(new TNumericLiteral("1")));
            Node expectedRightParam = new ASumSumExpr(new APrimaryExprProductExpr(lookupExpr), new TSumOp("+"), new AProductSumExpr(new APrimaryExprProductExpr(one)));;
            if (!Util.equals(rightParam, expectedRightParam)) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), "The parameter of the right hybrid fact expression must be " + expectedRightParam));
            }

            // 4) x does not occur free in bounds
            VariableReferenceCollector equivVarColl = new VariableReferenceCollector();
            equiv.distance.apply(equivVarColl);
            if (equivVarColl.getIds().contains(equivVar)) {
                handleCheckException(new CheckException(checkFile.peek(), node.getHybrid(), equivVar + " may not appear in the distance of the hybrid fact."));
            }

            // to form left and right rewrite expressions, substitute in start and end values
            PExpr left = (PExpr)equiv.left.clone();
            BoundsExprConverter conv = new BoundsExprConverter();
            ((Node)node.getStart().clone()).apply(conv);
            Map leftSubs = new HashMap();
            leftSubs.put(equivVar, Util.toPrimaryExpr(conv.getExpr()));
            Substituter sub = new Substituter(leftSubs);
            left.apply(sub);

            PExpr right = (PExpr)equiv.left.clone();
            conv = new BoundsExprConverter();
            ((Node)node.getEnd().clone()).apply(conv);
            Map rightSubs = new HashMap();
            rightSubs.put(equivVar, Util.toPrimaryExpr(conv.getExpr()));
            sub = new Substituter(rightSubs);
            right.apply(sub);

            ProofObligationEquiv topOb = peekEquivObligation(node.getHybrid().getLine(), node.getHybrid().getPos());
            PBoundsExpr distance = Util.multiplyBounds(Util.subtractBounds(node.getEnd(), node.getStart()), equiv.distance);
            Rewriter rewriter = new Rewriter(left, equiv.vars, right, distance,true, topOb.leftExpr);
            topOb.leftExpr.apply(rewriter);
            if (rewriter.getRewrites().isEmpty()) {
                handleCheckException(new CheckException(checkFile.peek(), factIdToken, "Found no occurences of " + left.toString() + " to rewrite."));
            }

            for (Rewriter.Rewrite curRewrite : rewriter.getRewrites()) {
                topOb.distance = Util.addBounds(topOb.distance, curRewrite.distance);
            }

            Optional<PExpr> topExpr = rewriter.getTopExpr();
            if (topExpr.isPresent()) {
                topOb.leftExpr = topExpr.get();
            }
        } else {
            handleCheckException(new CheckException(checkFile.peek(), factIdToken, "Undeclared symbol: " + factId));
        }
    }

    private void checkEquiv(PExpr left, PExpr right)  {

        PExpr leftCopy = (PExpr)left.clone();
        PExpr rightCopy = (PExpr)right.clone();

        ExprSimplifier simpl = new ExprSimplifier();
        leftCopy.apply(simpl);
        if (simpl.getTopExpr().isPresent()) {
            leftCopy = simpl.getTopExpr().get();
        }
        simpl = new ExprSimplifier();
        rightCopy.apply(simpl);
        if (simpl.getTopExpr().isPresent()) {
            rightCopy = simpl.getTopExpr().get();
        }

        if(!Util.equals(leftCopy, rightCopy)) {
            handleCheckException(new CheckException(checkFile.peek(), line, pos, leftCopy.toString() + "\n != \n" + rightCopy.toString()));
        }
    }

    private void checkBounds(PBoundsExpr actual, PBoundsExpr required) {

        BoundsAuto auto = new BoundsAuto(taskQueue, symbolTable, functions, axioms, classes);
        auto.check(checkFile.peek(), line, pos, actual, required);
    }

    private void handleCheckException(CheckException e) {
        throw new RuntimeException(e);
    }
}
