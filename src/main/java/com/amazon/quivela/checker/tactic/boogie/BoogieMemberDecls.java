/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.ANewExpr;
import com.amazon.quivela.parser.node.ANewParam;

import java.util.ArrayList;
import java.util.List;

public class BoogieMemberDecls extends DepthFirstAdapter {

    private final String name;
    private final PrettyPrintStream out;
    private int disable = 0;
    private List<String> members = new ArrayList();

    public BoogieMemberDecls(String name, PrettyPrintStream out) {
        this.name = name;
        this.out = out;
    }

    @Override
    public void inANewExpr(ANewExpr newExpr) {
        disable++;
    }
    @Override
    public void outANewExpr(ANewExpr newExpr) {
        disable--;
    }

    @Override
    public void outANewParam(ANewParam newParam) {
        if (disable == 0) {
            String member = newParam.getIdentifier().getText().trim();
            members.add(member);
            out.println("var " + name + "." + member + ":T;");
        }
    }

    public List<String> getMembers() {
        return members;
    }
}
