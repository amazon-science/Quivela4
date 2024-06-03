/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.TIdentifier;

import java.util.Map;

public class IdentifierSubstituter extends DepthFirstAdapter {

    private final Map<String, String> subs;

    public IdentifierSubstituter(Map<String, String> subs) {
        this.subs = subs;
    }

    @Override
    public void caseTIdentifier(TIdentifier ident) {
        String identStr = ident.getText().trim();
        String sub = subs.get(identStr);
        if (sub != null) {
            ident.replaceBy(new TIdentifier(sub));
        }
    }
}
