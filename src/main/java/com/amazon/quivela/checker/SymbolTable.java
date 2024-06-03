/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker;

import java.util.*;

public class SymbolTable {

    private Deque<SymbolTableFrame> frames = new ArrayDeque();

    public Type getType(String symbol) {
        for(SymbolTableFrame frame : frames) {
            if (frame.contains(symbol)) {
                return frame.getType(symbol);
            }
        }
        return null;
    }

    public boolean symbolModifiable(String symbol) {
        for(SymbolTableFrame frame : frames) {
            if (frame.contains(symbol)) {
                return !(frame.getKind() == SymbolTableFrame.Kind.CONSTANT);
            }
        }
        return false;
    }

    // cannot redeclare a variable in the same scope
    public boolean varDeclarationAllowed(String symbol) {

        return !frames.peek().contains(symbol);
    }

    public boolean varReferenceAllowed(String symbol) {
        for(SymbolTableFrame frame : frames) {
            if (frame.contains(symbol)) {
                return true;
            }
        }
        return false;
    }

    public void addSymbol(String symbol, Type type) {
        frames.peek().add(symbol, type);
    }

    public void addAll(Collection<String> symbols, Type type) {
        for(String s : symbols) {
            addSymbol(s, type);
        }
    }

    public void addAll(Collection<String> symbols, List<Type> types) {
        int i =0 ;
        for(String s : symbols) {
            addSymbol(s, types.get(i));
            i++;
        }
    }

    public void pushFrame(boolean isConstant) {
        frames.push(new SymbolTableFrame(isConstant ? SymbolTableFrame.Kind.CONSTANT : SymbolTableFrame.Kind.MUTABLE ));
    }

    public void pushLogicalFrame() {
        frames.push(new SymbolTableFrame(SymbolTableFrame.Kind.LOGICAL));
    }

    public SymbolTableFrame popFrame() {
        return frames.pop();
    }

    public SymbolTableFrame peekFrame() {
        return frames.peek();
    }

    public Set<String> allSymbols() {
        Set<String> result = new HashSet();
        for(SymbolTableFrame curFrame : frames) {
            result.addAll(curFrame.getIds());
        }
        return result;
    }

    public int getDepth() {
        return frames.size();
    }
}
