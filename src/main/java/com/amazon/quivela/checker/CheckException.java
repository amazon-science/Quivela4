/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker;

import com.amazon.quivela.parser.node.Token;

import java.io.File;

public class CheckException extends Exception {

    public CheckException(File f, int line, int pos, String msg) {
        super("error at " + f.getAbsolutePath() + "(" + line + "," + pos + "):\n"+ msg);
    }

    public CheckException(File f, Token tok, String msg) {
        this(f, tok.getLine(), tok.getPos(), msg);
    }

    public CheckException(Exception causedBy) {
        super(causedBy);
    }
}
