/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.ABisimProp;

import java.util.ArrayList;
import java.util.List;

public class BisimPropsCollector extends DepthFirstAdapter {

    private List<ABisimProp> props = new ArrayList();

    @Override
    public void outABisimProp(ABisimProp node) {
        props.add(node);
    }

    public List<ABisimProp> getProps() {
        return props;
    }
 }
