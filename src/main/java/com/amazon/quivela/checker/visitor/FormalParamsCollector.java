/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.checker.Type;
import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayList;
import java.util.List;

public class FormalParamsCollector extends AnalysisAdapter {

    private List<String> ids = new ArrayList<String>();
    private List<Type> types = new ArrayList();

    public static List<String> collect(Node func) {
        FormalParamsCollector collector = new FormalParamsCollector();
        func.apply(collector);
        return collector.getIds();
    }

    @Override
    public void caseAFormalParamsList(AFormalParamsList node)
    {
        if (node.getFormalParams() != null) {
            node.getFormalParams().apply(this);
        }
    }

    @Override
    public void caseAFormalParams(AFormalParams node)
    {
        node.getFormalParam().apply(this);
        for (Object obj : node.getFormalParamsTl()) {
            AFormalParamsTl tl = (AFormalParamsTl) obj;
            tl.getFormalParam().apply(this);
        }
    }

    @Override
    public void caseAFormalParam(AFormalParam node) {
        IdentifierAndTypeExtractor ext = new IdentifierAndTypeExtractor(node.getIdentifierAndType());
        ids.add(ext.getIdentifier());
        types.add(ext.getType());
    }

    public List<String> getIds() {
        return ids;
    }

    public List<Type> getTypes() {
        return types;
    }

}