/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.AFuncDecl;
import com.amazon.quivela.parser.node.AMethodDef;
import com.amazon.quivela.parser.node.AMethodList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodsCollector extends AnalysisAdapter {

    Map<String, AMethodDef> methods = new HashMap<>();
    List<String> methodNames = new ArrayList();

    @Override
    public void caseAMethodList(AMethodList node)
    {
        for(Object o : node.getMethodDef()) {
            AMethodDef curFunc = (AMethodDef)o;
            String funcName = curFunc.getIdentifier().getText();
            methods.put(funcName, curFunc);
            methodNames.add(funcName);
        }
    }

    public Map<String, AMethodDef> getMethods() {
        return methods;
    }

    public List<String> getMethodNames() {
        return methodNames;
    }
}
