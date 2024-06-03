/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.AnalysisAdapter;
import com.amazon.quivela.parser.node.*;

import java.util.ArrayList;
import java.util.List;

public class ActualParamsCollectorProp extends AnalysisAdapter {

    private List<PProp> props = new ArrayList();

    @Override
    public void caseAActualParamsProp(AActualParamsProp node) {
        AProps prop = (AProps)node.getProps();
        if (prop != null) {
            props.add((PProp)prop.getProp().clone());
            for (Object obj : prop.getPropsTl()) {
                APropsTl tl = (APropsTl) obj;
                props.add((PProp)tl.getProp().clone());
            }
        }
    }

    public List<PProp> getProps() {
        return props;
    }

}
