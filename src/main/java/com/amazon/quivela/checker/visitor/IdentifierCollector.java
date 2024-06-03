/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.TIdentifier;

import java.util.*;

public class IdentifierCollector extends DepthFirstAdapter {

    private final List<String> ids = new ArrayList();

    @Override
    public void caseTIdentifier(TIdentifier ident) {
        ids.add(ident.getText().trim());
    }

    public Collection<String> getIds() {
        return ids;
    }
}
