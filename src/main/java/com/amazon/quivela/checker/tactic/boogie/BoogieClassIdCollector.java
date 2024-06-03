/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.AClassIdIdentifierClassIdProp;
import com.amazon.quivela.parser.node.AClassIdInvalidClassIdProp;

public class BoogieClassIdCollector extends AnalysisAdapter {

    private String classId= null;

    public String getClassId() {
        return classId;
    }

    @Override
    public void caseAClassIdIdentifierClassIdProp(AClassIdIdentifierClassIdProp node) {
        classId = node.getIdentifier().getText().trim();
    }

    @Override
    public void caseAClassIdInvalidClassIdProp(AClassIdInvalidClassIdProp node) {
        classId = "internal.invalidClassId";
    }


}
