/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.util;

import java.io.OutputStream;
import java.io.PrintStream;

public class PrettyPrintStream {

    private int tab = 0;
    private final PrintStream stream;
    boolean newLine = true;

    public PrettyPrintStream(OutputStream out) {
        stream = new PrintStream(out);
    }

    public void pushTab() {
        tab++;
    }
    public void popTab() {
        tab--;
    }

    public void println() {
        stream.println();
        newLine = true;
    }

    public void println(String x) {
        print(x);
        println();
    }

    public void print(String x) {
        if (newLine) {
            for(int i = 0; i < tab; i++) {
                stream.print("  ");
            }
        }
        newLine = false;
        stream.print(x);
    }
    public void close() {
        stream.close();
    }
}
