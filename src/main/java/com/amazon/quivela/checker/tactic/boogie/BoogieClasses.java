/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.Util;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.HashMap;
import java.util.Map;

public class BoogieClasses {

    private Map<String, String> classesByDef = new HashMap();
    private Map<String, String> classesById = new HashMap();

    private static class NewClassConverter extends AnalysisAdapter {

        @Override
        public void caseANewExpr(ANewExpr node) {
            node.getNewParamsList().apply(this);
        }

        @Override
        public void caseANewParamsList(ANewParamsList node) {
            if (node.getNewParams() != null) {
                node.getNewParams().apply(this);
            }
        }

        @Override
        public void caseANewParams(ANewParams node) {
            node.getNewParam().apply(this);
            if(node.getNewParamsTl() != null) {
                for(Object curParamObj : node.getNewParamsTl()) {
                    ANewParamsTl curParam = (ANewParamsTl)curParamObj;
                    curParam.apply(this);
                }
            }
        }

        @Override
        public void caseANewParamsTl(ANewParamsTl node) {
            node.getNewParam().apply(this);
        }

        @Override
        public void caseANewParam(ANewParam node) {
            node.getExpr().replaceBy(Util.defaultExpr());
        }
    }

    private static String toClassDefString(PNewExpr newExpr) {
        NewClassConverter conv = new NewClassConverter();
        PNewExpr classDefClone = (PNewExpr)newExpr.clone();
        classDefClone.apply(conv);
        return classDefClone.toString().trim();
    }

    public void put(PNewExpr classDef, String classId) {

        String classDefString = toClassDefString(classDef);

        String existingClassDef = classesById.get(classId);
        if (existingClassDef != null && !existingClassDef.equals(classDefString)) {
            // TODO: better error handling
            throw new RuntimeException("Redefinition of class with name: " + classId);
        }

        String existingClassId = classesByDef.get(classDefString);
        if (existingClassId != null && !existingClassId.equals(classId)) {
            // TODO: better error handling
            throw new RuntimeException("Renaming of class with definition: " + classDefString);
        }

        classesByDef.put(classDefString, classId);
        classesById.put(classId, classDefString);
    }

    public void put(PNewExpr classDef) {
        String classId = freshClassId();
        put(classDef, classId);
    }

    public String getByDef(PNewExpr classDef) {
        return classesByDef.get(toClassDefString(classDef));
    }

    public String getById(String classId) {
        return classesById.get(classId);
    }

    public String freshClassId() {
        for(int i = 0; ; i++) {
            String classId = "internal.cls" + i;
            if(classesById.get(classId) == null) {
                return classId;
            }
        }
    }
}
