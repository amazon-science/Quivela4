/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.visitor;

import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.ACheckpointBisimPropScope;
import com.amazon.quivela.parser.node.AInvariantBisimPropScope;

import java.util.ArrayList;
import java.util.List;

public class BisimPropScopesCollector extends DepthFirstAdapter {

    public static class Checkpoint {
        private final String left;
        private final String right;

        public Checkpoint(String left, String right) {
            this.left = left;
            this.right = right;
        }

        public String getLeft() {
            return left;
        }
        public String getRight() {
            return right;
        }
    }

    private boolean invariant = false;
    private List<Checkpoint> checkpoints = new ArrayList();

    @Override
    public void outACheckpointBisimPropScope(ACheckpointBisimPropScope node) {
        checkpoints.add(new Checkpoint(node.getLeft().getText().trim(), node.getRight().getText().trim()));
    }

    @Override
    public void outAInvariantBisimPropScope(AInvariantBisimPropScope node) {
        invariant = true;
    }

    public boolean isInvariant() {
        return invariant;
    }
    public void setInvariant(boolean invariant) {
        this.invariant = invariant;
    }

    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }
}
