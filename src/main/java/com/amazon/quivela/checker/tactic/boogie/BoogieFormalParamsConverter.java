/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.checker.visitor.IdentifierAndTypeExtractor;
import com.amazon.quivela.parser.analysis.DepthFirstAdapter;
import com.amazon.quivela.parser.node.AFormalParam;
import com.amazon.quivela.parser.node.AFormalParamsTl;

import java.util.ArrayList;
import java.util.List;

public class BoogieFormalParamsConverter extends DepthFirstAdapter {
    private final PrettyPrintStream out;
    private final Mode mode;
    private boolean firstComma = false;
    private com.amazon.quivela.checker.Type defaultType;

    private List<String> paramNames = new ArrayList();
    int position = 0;

    public enum Mode {
        NAME_AND_TYPE,
        TYPE_ONLY,
        POSTIONAL_AND_TYPE,
        POSITIONAL_ONLY
    }

    public BoogieFormalParamsConverter(PrettyPrintStream out, com.amazon.quivela.checker.Type defaultType, Mode mode, boolean firstComma) {
        this.out = out;
        this.defaultType = defaultType;
        this.mode = mode;
        this.firstComma = firstComma;
    }

    public BoogieFormalParamsConverter(PrettyPrintStream out, com.amazon.quivela.checker.Type defaultType, Mode mode) {
        this(out, defaultType, mode,false);
    }

    @Override
    public void inAFormalParam(AFormalParam node) {
        if (firstComma) {
            firstComma = false;
            out.print(", ");
        }

        IdentifierAndTypeExtractor extractor = new IdentifierAndTypeExtractor(node.getIdentifierAndType());
        String paramName = extractor.getIdentifier();
        String strType = BoogieUtil.toBoogieType(extractor.getType(), defaultType).getBoogieString();


        switch(mode) {
            case NAME_AND_TYPE:
                out.print(paramName + ": " + strType);
                break;
            case TYPE_ONLY:
                out.println(strType);
                break;
            case POSTIONAL_AND_TYPE:
                out.println(("a" + position++) + ":" + strType );
                break;
            case POSITIONAL_ONLY:
                out.println("a" + position++);
                break;
        }

        paramNames.add(paramName);
    }

    @Override
    public void inAFormalParamsTl(AFormalParamsTl node) {
        out.print(", ");
    }

    public List<String> getParamNames() {
        return paramNames;
    }

}