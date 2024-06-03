/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.parser.node.*;
import com.amazon.quivela.util.PrettyPrintStream;
import com.amazon.quivela.Settings;
import com.amazon.quivela.checker.SymbolTable;
import com.amazon.quivela.checker.Type;
import com.amazon.quivela.checker.Util;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BoogieUtil {

    public static void writeAbstractFuncDecl(AFuncDecl decl, boolean isStatic, Type returnType, PrettyPrintStream out) {

        String funcName = decl.getIdentifier().getText().trim();

        out.print("function " + funcName + "#State(FunctionState");
        if (!isStatic) {
            out.print(", ObjectId, Heap");
        }
        BoogieFormalParamsConverter paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.TYPE_ONLY, true);
        decl.getFormalParamsList().apply(paramsConverter);

        out.println(") : FunctionState;");

        out.print("function " + funcName + "#Value(FunctionState");
        if (!isStatic) {
            out.print(", ObjectId, Heap");
        }
        paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.TYPE_ONLY, true);
        decl.getFormalParamsList().apply(paramsConverter);

        String returnTypeStr = BoogieUtil.toBoogieType(returnType).getBoogieString();
        out.println(") : " + returnTypeStr + ";");

        if (!isStatic) {
            out.print("function " + funcName + "#Heap(FunctionState");
            out.print(", ObjectId, Heap");

            paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.TYPE_ONLY, true);
            decl.getFormalParamsList().apply(paramsConverter);

            out.println(") : Heap;");
        }

        // the caller's object ID is passed in so non-static functions can use it when accessing the heap
        out.print("procedure " + funcName + "(internal.objectId : ObjectId");
        paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.POSTIONAL_AND_TYPE, true);
        decl.getFormalParamsList().apply(paramsConverter);

        out.println(") returns (r: " + returnTypeStr + ");");
        out.println("modifies functionState;");
        if (!isStatic) {
            out.println("modifies heap;");
        }

        out.print("ensures functionState == " + funcName + "#State(old(functionState)");
        if (!isStatic) {
            out.print(", internal.objectId, old(heap)");
        }
        paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.POSITIONAL_ONLY, true);
        decl.getFormalParamsList().apply(paramsConverter);

        out.println(");");

        out.print("ensures r == " + funcName + "#Value(old(functionState)");
        if (!isStatic) {
            out.print(", internal.objectId, old(heap)");
        }
        paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.POSITIONAL_ONLY, true);
        decl.getFormalParamsList().apply(paramsConverter);
        out.println(");");

        if (!isStatic) {
            out.print("ensures heap == " + funcName + "#Heap(old(functionState)");
            out.print(", internal.objectId, old(heap)");

            paramsConverter = new BoogieFormalParamsConverter(out, Type.Opaque, BoogieFormalParamsConverter.Mode.POSITIONAL_ONLY, true);
            decl.getFormalParamsList().apply(paramsConverter);
            out.println(");");
        }
    }

    public static void writePureFuncDecl(AFuncDecl decl, Type returnType, PrettyPrintStream out) {

        String funcName = decl.getIdentifier().getText().trim();
        out.print("function " + funcName + "(");
        BoogieFormalParamsConverter paramsConverter = new BoogieFormalParamsConverter(out, com.amazon.quivela.checker.Type.Bitstring, BoogieFormalParamsConverter.Mode.NAME_AND_TYPE,false);
        decl.getFormalParamsList().apply(paramsConverter);

        String returnTypeStr = BoogieUtil.toBoogieType(returnType).getBoogieString();
        out.println(") : " + returnTypeStr + ";");
    }

    public static void writeProcFuncDecl(AFuncDecl decl, SymbolTable symbolTable, BoogieFunctions functions, BoogieMethods methods, BoogieClasses classes, BoogieConstants constants, Type returnType, PrettyPrintStream out) {

        String returnTypeStr = BoogieUtil.toBoogieType(returnType).getBoogieString();
        BoogieClassDecls classDecls = new BoogieClassDecls(symbolTable, functions, methods, classes, constants, out);
        decl.apply(classDecls);

        String methodName = decl.getIdentifier().getText().trim();
        out.print("procedure {:inline 1} " + methodName + "(internal.objectId : ObjectId");
        BoogieFormalParamsConverter paramsConverter = new BoogieFormalParamsConverter(out, com.amazon.quivela.checker.Type.Opaque, BoogieFormalParamsConverter.Mode.NAME_AND_TYPE, true);

        decl.getFormalParamsList().apply(paramsConverter);
        out.println(") returns (result : " + returnTypeStr + ")");
        out.println("modifies checkpoints;");
        out.println("modifies functionState;");
        out.println("modifies heap; {");
        out.pushTab();

        symbolTable.pushFrame(true);
        symbolTable.addAll(paramsConverter.getParamNames(), Type.Opaque);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrettyPrintStream exprOut = new PrettyPrintStream(baos);
        exprOut.pushTab();
        BoogieExprConverter converter = new BoogieExprConverter(symbolTable, functions, methods, classes, new HashMap(), new HashMap(), exprOut);
        decl.getFuncBody().apply(converter);

        for(String curVar : converter.getDeclaredVars().keySet()) {
            String type = converter.getDeclaredVars().get(curVar);
            out.println("var " + curVar + ":" + type + ";");
        }
        out.println();
        for(String curVar : converter.getDeclaredVars().keySet()) {
            String type = converter.getDeclaredVars().get(curVar);
            if (type.equals("T")) {
                out.println(curVar + " := defaultValue;");
            }
        }
        out.println(baos.toString());

        Value opaqueValue = new Value(BoogieType.Opaque, converter.getValue());
        out.println("result := " + opaqueValue.getValue(BoogieUtil.toBoogieType(returnType)) + ";");
        out.println();
        out.popTab();
        out.println("}");

        symbolTable.popFrame();

    }

    public static void writeFuncDecls(SymbolTable symbolTable, BoogieFunctions functions, BoogieMethods methods, BoogieClasses classes, BoogieConstants constants, PrettyPrintStream out) {

        for(String funcName : functions.getNames()) {
            AFuncDecl funcDecl = functions.getByName(funcName);

           Type returnType = Util.functionReturnType(funcDecl);
            if (Util.isPure(funcDecl)) {
                writePureFuncDecl(funcDecl, returnType, out);
            } else if (funcDecl.getFuncBody() == null){
                writeAbstractFuncDecl(funcDecl, Util.isStatic(funcDecl), returnType, out);
            } else {
                writeProcFuncDecl(funcDecl, symbolTable, functions, methods, classes, constants, returnType, out);
            }
            out.println();
        }
    }

    public static void writePrelude(PrettyPrintStream out) {

        // Type of opaque Quivela values which are stored in memory. The only operations allowed on opaque types are load/store and conversion to/from other types.
        out.println("type T;");
        out.println("const defaultValue : T;");

        // Opaque values can be converted to/from values of the following types.

        // Bit strings are lists of bits with arbitrary length. They are also used as memory addresses.
        // Converting a value of some other type to a bit string produces nil.
        out.println("type Bitstring;");
        out.println("function toBitstring(T) returns (Bitstring);");
        out.println("function fromBitstring(Bitstring) returns (T);");
        out.println("axiom (forall b: Bitstring :: toBitstring(fromBitstring(b)) == b);");
        // The empty bit string, which is used to define the default value
        out.println("const nil : Bitstring;");
        out.println("axiom (defaultValue == fromBitstring(nil));");


        // Object references can only be used to invoke methods on objects. The ID in the reference is used to locate the target object.
        // Code cannot examine references or learn anything about them by any means other than invoking methods using them.
        // Converting a value of some other type to a reference results in a default value for which all invocations are no-ops.
        out.println("type ObjectId;");
        out.println("function toObjectId(T) returns (ObjectId);");
        out.println("function fromObjectId(ObjectId) returns (T);");
        out.println("axiom (forall id: ObjectId :: toObjectId(fromObjectId(id)) == id);");
        // Fresh object IDs can be created from a parent ID and the heap. The new object ID is not present in the heap.
        // The new object ID includes the object ID of the creator, so two different objects will never create the same child object ID.
        // This ensures that all object IDs in the heap are unique.
        out.println("function freshObjectId(ObjectId, Heap): ObjectId;");

        // Memories (maps) hold object attribute values, and memories can be encoded in opaque values.
        // Converting a value of some other type to a memory produces an empty memory.
        out.println("type Memory = [Bitstring]T;");
        out.println("function toMemory(T) returns (Memory);");
        out.println("function fromMemory(Memory) returns(T);");
        out.println("axiom (forall m:Memory :: toMemory(fromMemory(m)) == m);");
        out.println("axiom (forall m:Memory :: toBitstring(fromMemory(m)) != nil);");
        out.println("axiom (forall v:Bitstring :: toMemory(defaultValue)[v]==defaultValue);");


        // TODO: built-in tuples and related axioms
        // Method calls are implemented by storing parameters into tuples, encoding the tuple to an
        // opaque value, and extracting the tuple into local variables to execute the body of the method.

        // Built-in support for Booleans, integers, and reals, which are represented using bit strings
        out.println("function toInt(Bitstring) : int;");
        out.println("function fromInt(int) returns (Bitstring);");
        out.println("axiom (forall n:int :: toInt(fromInt(n)) == n);");
        out.println("axiom(fromInt(0) == nil);");
        out.println("axiom (forall n : int :: n != 0 ==> fromInt(n) != nil);");
        out.println("axiom (forall n1 : int, n2 : int :: n1 != n2 ==> fromInt(n1) != fromInt(n2));");
        out.println("axiom (forall n1 : Bitstring, n2 : Bitstring :: n1 != n2 ==> toInt(n1) != toInt(n2));");

        out.println("function fromBool(bool) returns (Bitstring);");
        out.println("axiom(fromBool(false) == nil);");
        out.println("axiom(fromBool(true) != nil);");

        // TODO: we probably don't need reals to be part of the language. We only need them for bounds expressions, which could be separated in the parse tree.
        out.println("function toReal(Bitstring) returns (real);");
        out.println("function fromReal(real) returns (Bitstring);");

        // Boogie and solvers don't seem to handle exponents very well, so we axiomatize them
        out.println("function real_pow(real, real) : real;");
        out.println("axiom (forall r1: real, r2 : real :: real(0) <= r1 ==> real(0) <= real_pow(r1, r2));");

        // Seems to be problems with real division, too
        out.println("axiom (forall a : real, b : real :: real(0) <= a ==> real(0) <= b ==> real(0) <= a / b);");

        // The type of the state which is used by functions that are not class members
        out.println("type FunctionState;");


        //out.println("const invalidObjectId : ObjectId;");
        out.println("type MethodRef;");
        out.println("type ObjAttr kind;");

        out.println("type Object = <kind>[ObjAttr kind] kind;");
        // A heap is an abstract representation as a list of objects, where each object has an ID
        // When executing within a method, there is a map of object fields and a heap containining potential targets for method invocation
        // To invoke a method, the heap is split in two at the location of the target object, and the method gets the object fields and the second half of the heap
        // After the invocation, the heap is reassembled.
        // When creating a new object, a new ID is created in a way that ensures that any objects in the heap with that ID are invalid
        // So a lookup in the heap after a new produces the same object as before, or produces an invalid object
        out.println("type Heap;");
        out.println("type ClassId;");

        // TODO: Expr should not be in language---only in bounds exprs
        out.println("type Expr;");

        out.println("const unique internal.invalidClassId : ClassId;");

        out.println("const unique objectIdAttr : ObjAttr ObjectId;");
        out.println("const unique objectMemoryAttr : ObjAttr Memory;");
        out.println("const unique objectClassIdAttr : ObjAttr ClassId;");

        out.println("const emptyObject : Object;");

        //out.println("function fromString(string) returns (T);");
        out.println("function fromExpr(Expr) returns (T);");

        // splitting and re-assembling heaps for method invocation
        out.println("type SplitHeapAttribute kind;");
        out.println("type SplitHeap = <kind>[SplitHeapAttribute kind] kind;");
        out.println("const unique heapLeft : SplitHeapAttribute Heap;");
        out.println("const unique heapRight : SplitHeapAttribute Heap;");
        out.println("const unique targetObject : SplitHeapAttribute Object;");
        out.println("function invokeSplit(Heap, ObjectId) : SplitHeap;");
        out.println("function assembleHeap(SplitHeap) : Heap;");
        out.println("function addObject(Heap, Object) : Heap;");
        out.println("function objectValid(Heap, ObjectId) : bool;");

        out.println("axiom (forall id:ObjectId, h : Heap, o : Object :: objectValid(h, id) ==> (id != o[objectIdAttr]) ==> (objectValid(addObject(h, o), id) && invokeSplit(addObject(h, o), id)[targetObject] == invokeSplit(h, id)[targetObject]));");
        out.println("axiom (forall h : Heap, id : ObjectId :: objectValid(h, id) <==> (invokeSplit(h, id)[targetObject][objectClassIdAttr] != internal.invalidClassId));");

        // relational frame conditions
        // The predicate implies that the objects have the same IDs. This could be generalized to allow objects with different IDs, but it would be more complicated.
        // The general form implies that all references in the memories are framed, and all values are equal
        out.println("function frame(Heap, Heap, ObjectId, ObjectId) returns (bool);");
        out.println("axiom (forall h:Heap, x:ObjectId :: frame(h, h, x, x));");
        out.println("axiom (forall h1:Heap, h2:Heap, o1:ObjectId, o2:ObjectId :: frame(h1, h2, o1, o2) ==> (\n" +
                "    o1 == o2 &&\n" +
                "    invokeSplit(h1, o1)[targetObject] == invokeSplit(h2, o2)[targetObject]  &&\n" +
                "    invokeSplit(h1, o1)[heapRight] == invokeSplit(h2, o2)[heapRight]));");

        // splitting and reassembling on framed object IDs preserves frame condition
        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, s1 : SplitHeap, s2 : SplitHeap ::\n" +
                "    frame(h1, h2, id1, id2) ==>\n" +
                "    s1[heapLeft] == invokeSplit(h1, id1)[heapLeft] ==>\n" +
                "    s2[heapLeft] == invokeSplit(h2, id2)[heapLeft] ==>\n" +
                "    s1[targetObject][objectIdAttr] == invokeSplit(h1, id1)[targetObject][objectIdAttr]  ==>\n" +
                "    s2[targetObject][objectIdAttr] == invokeSplit(h2, id2)[targetObject][objectIdAttr] ==>\n" +
                "    s1[targetObject] == s2[targetObject]==>\n" +
                "    s1[heapRight] == s2[heapRight] ==>\n" +
                "    frame(assembleHeap(s1), assembleHeap(s2), id1, id2)\n" +
                ");");

        // splitting and reassembling on a different pair of framed object IDs preserves frame conditions
        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, id1' : ObjectId, id2' : ObjectId, s1 : SplitHeap, s2 : SplitHeap ::\n" +
                "    frame(h1, h2, id1, id2) ==>\n" +
                "    frame(h1, h2, id1', id2') ==> \n" +
                "    s1[heapLeft] == invokeSplit(h1, id1')[heapLeft] ==>\n" +
                "    s2[heapLeft] == invokeSplit(h2, id2')[heapLeft] ==>\n" +
                "    s1[targetObject][objectIdAttr] == invokeSplit(h1, id1')[targetObject][objectIdAttr] ==>\n" +
                "    s2[targetObject][objectIdAttr] == invokeSplit(h1, id2')[targetObject][objectIdAttr]  ==>\n" +
                "    s1[targetObject] == s2[targetObject] ==>\n" +
                "    s1[heapRight] == s2[heapRight] ==>\n" +
                "    frame(assembleHeap(s1), assembleHeap(s2), id1, id2)\n" +
                ");");

        // splitting and assembling in one program on a different ID preserves frame condition
        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, s1 : SplitHeap, id1' : ObjectId ::\n" +
                "    frame(h1, h2, id1, id2) ==>\n" +
                "    id1' != id1 ==>\n" +
                "    id1' != id2 ==>\n" +
                "    s1[heapLeft] == invokeSplit(h1, id1')[heapLeft] ==>\n" +
                "    s1[heapRight] == invokeSplit(h1, id1')[heapRight] ==>\n" +
                "    s1[targetObject][objectIdAttr] == id1' ==>\n" +
                "    frame(assembleHeap(s1), h2, id1, id2));");
        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, s2 : SplitHeap, id2' : ObjectId ::\n" +
                "    frame(h1, h2, id1, id2) ==>\n" +
                "    id2' != id1 ==>\n" +
                "    id2' != id2 ==>\n" +
                "    s2[heapLeft] == invokeSplit(h2, id2')[heapLeft] ==>\n" +
                "    s2[heapRight] == invokeSplit(h2, id2')[heapRight] ==>\n" +
                "    s2[targetObject][objectIdAttr] == id2' ==>\n" +
                "    frame(h1, assembleHeap(s2), id1, id2));");

        /*/
        out.println("axiom (forall s1 : SplitHeap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, id1' : ObjectId ::\n" +
                "    frame(s1[heapRight], h2, id1, id2) ==>\n" +
                "    id1' != id1 ==>\n" +
                "    id1' != id2 ==>\n" +
                "    (!objectValid(s1[heapLeft], id1')) ==>\n" +
                "    s1[targetObject][objectIdAttr] == id1' ==>\n" +
                "    frame(invokeSplit(assembleHeap(s1), id1')[heapRight], h2, id1, id2)\n" +
                ");");

        out.println("axiom (forall h1 : Heap, s2 : SplitHeap, id1 : ObjectId, id2 : ObjectId, id2' : ObjectId ::\n" +
                "    frame(h1, s2[heapRight], id1, id2) ==>\n" +
                "    id2' != id1 ==>\n" +
                "    id2' != id2 ==>\n" +
                "    (!objectValid(s2[heapLeft], id2')) ==>\n" +
                "    s2[targetObject][objectIdAttr] == id2' ==>\n" +
                "    frame(h1, invokeSplit(assembleHeap(s2), id2')[heapRight], id1, id2)\n" +
                ");");
        */

        // frame conditions can be moved up or down the heap
        out.println("axiom(forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, id' : ObjectId ::\n" +
                "    frame(invokeSplit(h1, id')[heapRight], h2, id1, id2) ==>\n" +
                "    objectValid(invokeSplit(h1, id1)[heapLeft], id') ==>\n" +
                "    frame(h1, h2, id1, id2)\n" +
                ");");

        out.println("axiom(forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, id' : ObjectId ::\n" +
                "    frame(h1, invokeSplit(h2, id')[heapRight], id1, id2) ==>\n" +
                "    objectValid(invokeSplit(h2, id2)[heapLeft], id') ==>\n" +
                "    frame(h1, h2, id1, id2)\n" +
                ");");

        out.println("axiom(forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, id' : ObjectId ::\n" +
                "    frame(h1, h2, id1, id2) ==>\n" +
                "    objectValid(invokeSplit(h1, id1)[heapLeft], id') ==>\n" +
                "    frame(invokeSplit(h1, id')[heapRight], h2, id1, id2)\n" +
                ");");

        out.println("axiom(forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, id' : ObjectId ::\n" +
                "    frame(h1, h2, id1, id2) ==>\n" +
                "    objectValid(invokeSplit(h2, id2)[heapLeft], id') ==>\n" +
                "    frame(h1, invokeSplit(h2, id')[heapRight], id1, id2)\n" +
                ");");

        /*
        out.println("axiom (forall s1:SplitHeap, s2:SplitHeap, o1:ObjectId, o2:ObjectId ::\n" +
                "    (s1[targetObject] == s2[targetObject]) ==>\n" +
                "    (s1[heapRight] == s2[heapRight]) ==>\n" +
                "    (exists h1:Heap, h2:Heap, x1:ObjectId, x2:ObjectId :: (frame(h1, h2, x1, x2) && s1[heapLeft] == invokeSplit(h1, x1)[heapLeft] && s2[heapLeft] == invokeSplit(h2, x2)[heapLeft])) ==>\n" +
                "    (frame(assembleHeap(s1), assembleHeap(s2), o1, o2)));");
         */

        // newly added objects are valid in left subheaps
        out.println("axiom (forall h : Heap, id1 : ObjectId, o2 : Object ::\n" +
                "    o2[objectClassIdAttr] != internal.invalidClassId ==>" +
                "    id1 != o2[objectIdAttr] ==>" +
                "    objectValid(invokeSplit(addObject(h, o2), id1)[heapLeft], o2[objectIdAttr]));\n");

        // adding objects does not affect validity of objecs in left subheaps as long as we are not splitting on the new object ID
        out.println("axiom (forall h : Heap, id1 : ObjectId, id2 : ObjectId, o : Object ::\n" +
                "    objectValid(invokeSplit(h, id2)[heapLeft], id1) ==> o[objectIdAttr] != id2 ==> objectValid(invokeSplit(addObject(h, o), id2)[heapLeft], id1));");

        out.println("axiom (forall h : Heap, id1 : ObjectId, id2 : ObjectId, id3 : ObjectId ::\n" +
                "    objectValid(invokeSplit(h, id2)[heapLeft], id1) ==>\n" +
                "    objectValid(invokeSplit(h, id3)[heapLeft], id1) ==>\n" +
                "    objectValid(invokeSplit(invokeSplit(h, id3)[heapLeft], id2)[heapLeft], id1)\n" +
                "    );");

        out.println("axiom (forall s : SplitHeap, id1 : ObjectId, id2 : ObjectId ::\n" +
                "    objectValid(invokeSplit(s[heapLeft], id1)[heapLeft], id2) ==>\n" +
                "    objectValid(invokeSplit(assembleHeap(s), id1)[heapLeft], id2)\n" +
                ");");

        // Heaps and split heaps ensure that IDs do not cause issues. For example:
        //  * In a split heap, the target object ID does not appear in heapLeft
        //  * In heaps and subheaps, child IDs never appear before parent IDs
        out.println("axiom (forall s : SplitHeap, id : ObjectId :: s[targetObject][objectIdAttr] == id ==> invokeSplit(assembleHeap(s), id) == s);");

        out.println("axiom (forall s : SplitHeap, h : Heap :: !objectValid(s[heapLeft], freshObjectId(s[targetObject][objectIdAttr], h)));");

        out.println("axiom (forall s : SplitHeap, id : ObjectId, id' : ObjectId :: id != id' ==> s[targetObject][objectClassIdAttr] != internal.invalidClassId ==> s[targetObject][objectIdAttr] == id ==> (!objectValid(s[heapLeft], id')) ==> objectValid(invokeSplit(assembleHeap(s), id')[heapLeft], id));");
        out.println("axiom (forall h : Heap, id : ObjectId, id' : ObjectId :: id != id' ==> objectValid(invokeSplit(h, id')[heapLeft], id) ==> !objectValid(invokeSplit(h, id)[heapLeft], id'));");

        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, o : Object :: frame(h1, h2, id1, id2) ==> (o[objectIdAttr] != id1) ==> frame(addObject(h1, o), h2, id1, id2));");
        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, o : Object :: frame(h1, h2, id1, id2) ==> (o[objectIdAttr] != id2) ==> frame(h1, addObject(h2, o), id1, id2));");

        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, o : Object :: frame(h1, h2, id1, id2) ==> frame(invokeSplit(addObject(h1, o), o[objectIdAttr])[heapRight], h2, id1, id2));");
        out.println("axiom (forall h1 : Heap, h2 : Heap, id1 : ObjectId, id2 : ObjectId, o : Object :: frame(h1, h2, id1, id2) ==> frame(h1, invokeSplit(addObject(h2, o), o[objectIdAttr])[heapRight], id1, id2));");


        out.println("axiom (forall h:Heap, o:Object :: invokeSplit(addObject(h, o), o[objectIdAttr])[targetObject] == o);");

        out.println("axiom (forall h : Heap, id : ObjectId, id' : ObjectId :: " +
                "(objectValid(invokeSplit(h, id')[heapLeft], id) && invokeSplit(invokeSplit(h, id')[heapLeft], id)[targetObject] == invokeSplit(h, id)[targetObject]) || " +
                "id==id' || " +
                "(!objectValid(invokeSplit(h, id')[heapLeft], id) && invokeSplit(invokeSplit(h, id')[heapRight], id)[targetObject] == invokeSplit(h, id)[targetObject]));");

        out.println("axiom (forall s : SplitHeap, id : ObjectId :: objectValid(s[heapLeft], id) ==> (objectValid(assembleHeap(s), id) && invokeSplit(assembleHeap(s), id)[targetObject] == invokeSplit(s[heapLeft], id)[targetObject]));");
        //out.println("axiom (forall s : SplitHeap, id : ObjectId :: !objectValid(s[heapLeft], id) ==> id == s[targetObject][objectIdAttr] ==> (invokeSplit(assembleHeap(s), id)[targetObject] == s[targetObject]));");
        out.println("axiom (forall s : SplitHeap, id : ObjectId :: !objectValid(s[heapLeft], id) ==> id != s[targetObject][objectIdAttr] ==> (invokeSplit(assembleHeap(s), id)[targetObject] == invokeSplit(s[heapRight], id)[targetObject]));");
        out.println("axiom (forall s : SplitHeap, id : ObjectId :: !objectValid(s[heapLeft], id) ==> id != s[targetObject][objectIdAttr] ==> (objectValid(assembleHeap(s), id)==objectValid(s[heapRight], id) && invokeSplit(assembleHeap(s), id)[targetObject] == invokeSplit(s[heapRight], id)[targetObject]));");

        out.println("axiom (forall h:Heap, id : ObjectId :: objectValid(h, id) ==> invokeSplit(h, id)[targetObject][objectIdAttr] == id);");
        out.println("axiom (forall h:Heap, id : ObjectId :: !objectValid(invokeSplit(h, id)[heapLeft], id));");
        out.println("axiom (forall id : ObjectId, h:Heap :: !objectValid(h, freshObjectId(id, h)));");
        //out.println("axiom (forall h:Heap, o : Object :: o[objectClassIdAttr] != internal.invalidClassId ==> objectValid(addObject(h, o), o[objectIdAttr]));");
        //out.println("axiom (forall h : Heap, id : ObjectId, o : Object :: [objectClassIdAttr] != internal.invalidClassId ==> objectValid(h, id) ==> objectValid(addObject(h, o), id));");
        out.println("axiom (forall id : ObjectId, h : Heap :: freshObjectId(id, h) != id);");

        out.println("function isBits(T) : bool;");
        out.println("axiom (forall x : Bitstring :: isBits(fromBitstring(x)));");
        out.println("axiom (forall h:Heap, x : T :: isBits(x) ==> !objectValid(h, toObjectId(x)));");
        out.println("axiom (forall x1 : T, x2 : T :: isBits(x1) ==> isBits(x2) ==> toBitstring(x1) == toBitstring(x2) ==> x1==x2);");

        //out.println("axiom (forall v1 : T, v2 : T :: toObjectId(v1) == toObjectId(v2) ==> v1 == v2);");

        out.println("type CheckpointAttr kind;");
        out.println("const unique checkpointHeap : CheckpointAttr Heap;");
        out.println("const unique checkpointSplitHeap : CheckpointAttr SplitHeap;");
        out.println("const unique checkpointMemory : CheckpointAttr Memory;");
        out.println("const unique checkpointValue : CheckpointAttr T;");
        out.println("const unique checkpointFunctionState : CheckpointAttr FunctionState;");
        out.println("const unique checkpointValid : CheckpointAttr bool;");
        out.println("type Checkpoint = <kind>[CheckpointAttr kind]kind;");
        out.println("type CheckpointId;");
        out.println("type Checkpoints = [CheckpointId]Checkpoint;");
        out.println("const unique initCheckpoints : Checkpoints;");
        out.println("axiom (forall id : CheckpointId :: !initCheckpoints[id][checkpointValid]);");

        out.println("var functionState : FunctionState;");

        out.println("var heap : Heap;");
        out.println("var heap1 : Heap;");
        out.println("var heap2 : Heap;");

        out.println("var objectMemory : Memory;");
        out.println("var objectMemory1 : Memory;");
        out.println("var objectMemory2 : Memory;");

        out.println("var checkpoints : Checkpoints;");
        out.println("var checkpoints1 : Checkpoints;");
        out.println("var checkpoints2 : Checkpoints;");
        out.println("const emptyCheckpoint : Checkpoint;");

        out.println();
    }

    public static void writeSymbols(SymbolTable symbolTable, PrettyPrintStream out) {
        for (String curId : symbolTable.allSymbols()) {
            Type type = symbolTable.getType(curId);
            if (type == null) {
                throw new RuntimeException("oops");
            }
            String boogieType = toBoogieType(type).getBoogieString();
            out.println("const " + curId + ":" + boogieType +";");
        }
        out.println();
    }

    public static Map<String, String> getScopedVars(Collection<String> fields, String mapName) {
        Map<String, String> result = new HashMap();
        for(String curMember : fields) {
            result.put(curMember, mapName + "[internal.attribute.field." + curMember + "]");
        }
        return result;
    }

    public static void writeAxioms(SymbolTable symbolTable, Collection<AAxiomDecl> axioms, BoogieConstants constants, Map<String, ANewExpr> classes, BoogieFunctions functions, PrettyPrintStream out) {
        for(AAxiomDecl axiomDecl : axioms) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrettyPrintStream axiomOut = new PrettyPrintStream(baos);

            axiomOut.print("axiom (");
            BoogiePropConverter propConverter = new BoogiePropConverter(symbolTable, constants, classes, functions, new HashMap(), out);
            axiomDecl.getProp().apply(propConverter);
            axiomOut.print(propConverter.getValueString());
            axiomOut.print(");");
            axiomOut.close();
            out.println(baos.toString());
        }
        out.println();
    }

    public static String toCheckpointId(String checkpointName) {
        return "internal.checkpointid." + checkpointName;
    }

    public static void declareTargetMethod(String methodName, int numArgs, PrettyPrintStream out) {
        out.print("function " + methodName + "#Heap(h: Heap, o: Memory, f : FunctionState, id : ClassId, oid : ObjectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i + ":T");
        }
        out.println("): Heap;");

        out.print("function " + methodName + "#Memory(h: Heap, o: Memory, f : FunctionState, id : ClassId, oid : ObjectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i + ":T");
        }
        out.println("): Memory;");

        out.print("function " + methodName + "#Value(h: Heap, o: Memory, f : FunctionState, id : ClassId, oid : ObjectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i + ":T");
        }
        out.println("): T;");

        out.print("function " + methodName + "#FunctionState(h: Heap, o: Memory, f : FunctionState, id : ClassId, oid : ObjectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i + ":T");
        }
        out.println("): FunctionState;");

        out.print("procedure " + methodName + "(internal.classId : ClassId, internal.objectId : ObjectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(",a" + i + ":T");
        }
        out.println(") returns (r: T);");
        out.println("modifies heap;");
        out.println("modifies objectMemory;");
        out.println("modifies functionState;");
        out.print("ensures heap == " + methodName + "#Heap(old(heap), old(objectMemory), old(functionState), internal.classId, internal.objectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i);
        }
        out.println(");");

        out.print("ensures objectMemory == " + methodName + "#Memory(old(heap), old(objectMemory), old(functionState), internal.classId, internal.objectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i);
        }
        out.println(");");

        out.print("ensures r == " + methodName + "#Value(old(heap), old(objectMemory), old(functionState), internal.classId, internal.objectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i );
        }
        out.println(");");

        out.print("ensures functionState == " + methodName + "#FunctionState(old(heap), old(objectMemory), old(functionState), internal.classId, internal.objectId");
        for(int i = 0; i < numArgs; i++) {
            out.print(", a" + i);
        }
        out.println(");");

        out.println("ensures (forall id:ObjectId :: objectValid(old(heap), id) ==> (objectValid(heap, id) && invokeSplit(heap, id)[targetObject][objectClassIdAttr] == invokeSplit(old(heap), id)[targetObject][objectClassIdAttr]));");

        out.println();

    }

    public static void declareTargetMethods(Map<String, Integer> methods, PrettyPrintStream out) {
        for(String methodName : methods.keySet()) {
            declareTargetMethod(methodName, methods.get(methodName), out);
        }
    }

    private static class BoogieCache {

        Set<String> digests = new HashSet();

        public boolean contains(String boogieString) {
            return digests.contains(digest(boogieString));
        }

        private String digest(String boogieString) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-384");
                byte[] digestBytes = md.digest(boogieString.getBytes());
                return Base64.getEncoder().encodeToString(digestBytes);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public void addDigest(String digest) {
            digests.add(digest);
        }

        public void add(String boogieString) {
            addDigest(digest(boogieString));
        }

        public Stream<String> getDigests() {
            return digests.stream();
        }
    }

    private static BoogieCache boogieCache = null;

    private static String getBoogieCacheFilename() {
        return "quivela.cache.boogie";
    }

    private static BoogieCache loadBoogieCache() throws IOException {
        BoogieCache result = new BoogieCache();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(getBoogieCacheFilename()));
            reader.lines().forEach(result::addDigest);
            reader.close();
        } catch (FileNotFoundException ex) {
            // start with empty cache
        }

        return result;
    }

    private static BoogieCache getBoogieCache() throws IOException {
        if (boogieCache == null) {
            boogieCache = loadBoogieCache();
        }

        return boogieCache;
    }

    public static boolean isCached(String boogieString) throws IOException {
        return getBoogieCache().contains(boogieString);
    }

    public static void cache(String boogieProgram) throws IOException {
        BoogieCache cache = getBoogieCache();
        cache.add(boogieProgram);
        writeBoogieCache(cache);
    }

    private static void writeBoogieCache(BoogieCache cache) throws IOException {
        PrintWriter fileOut = new PrintWriter(new FileWriter(getBoogieCacheFilename()));
        cache.getDigests().forEach(fileOut::println);
        fileOut.close();
    }

    public static Process initVerify(int taskId, String boogieIn) throws IOException {

        String boogieFilename = getBoogieFile(taskId);
        PrintWriter fileOut = new PrintWriter(new FileWriter(boogieFilename));
        fileOut.println(boogieIn);
        fileOut.close();

        ProcessBuilder procBuilder = new ProcessBuilder();
        String boogiePath = getBoogiePath();
        return procBuilder.command(boogiePath, /* "/proverOpt:O:smt.qi.eager-threshold=20", */ boogieFilename).start();
    }

    public static boolean boogieOutSuccess(String boogieOut) {
        BufferedReader reader = new BufferedReader(new StringReader(boogieOut));
        String line;
        Pattern pattern = Pattern.compile("Boogie program verifier finished with \\d+ verified, 0 errors$");
        try {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return true;
                }
            }
        } catch (IOException ex) {
            // can't happen
            throw new RuntimeException(ex);
        }

        return false;
    }

    /*
    public static boolean verify(int taskId, String boogieIn) throws IOException {

        Process proc = initVerify(taskId, boogieIn);
        BufferedReader procIn = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        String line = procIn.readLine();
        List<String> boogieOut = new ArrayList();
        while(line != null ) {
            boogieOut.add(line);
            line = procIn.readLine();
        }
        procIn.close();

        boolean result = boogieOutSuccess(boogieOut);

        if (!result) {
            for(String curLine : boogieOut) {
                System.err.println(curLine);
            }
            System.err.println();
        }

        return result;
    }
     */

    public static String freshExprConstId(Collection<String> existing) {
        for(int i = 0; ; i++) {
            String name = "internal.expr" + i;
            if (!existing.contains(name)) {
                return name;
            }
        }
    }

    public static void saveCheckpoint(PrettyPrintStream out, String name, String value, String splitHeapId) {
        out.println("// saving checkpoint for label " + name);
        String checkpointId = toCheckpointId(name);
        out.println("checkpoints[" + checkpointId + "] := emptyCheckpoint[checkpointFunctionState := functionState][checkpointHeap := heap][checkpointMemory := objectMemory];");

        //out.println("checkpoints[" + checkpointId + "][checkpointFunctionState] := functionState;");
        //out.println("checkpoints[" + checkpointId + "][checkpointHeap] := heap;");
        //out.println("checkpoints[" + checkpointId + "][checkpointMemory] := objectMemory;");
        //out.println("checkpoints[" + checkpointId + "][checkpointValue] := " + value + ";");
        //out.println("checkpoints[" + checkpointId + "][checkpointValid] := true;");
        //if (splitHeapId != null) {
        //    out.println("checkpoints[" + checkpointId + "][checkpointSplitHeap] := " + splitHeapId + ";");
        //}

    }

    public static void saveCheckpoint(PrettyPrintStream out, String name, String value) {
        saveCheckpoint(out, name, value, null);
    }

    private static String getBoogiePath(){
        return Settings.boogiePath;
    }

    public static String getBoogieFile(int index) {
        return "boogie" + index + ".bpl";
    }

    public static BoogieType toBoogieType(com.amazon.quivela.checker.Type type, com.amazon.quivela.checker.Type defaultType) {
        if (type == null) {
            return toBoogieType(defaultType);
        } else {
            return toBoogieType(type);
        }
    }
    public static BoogieType toBoogieType(com.amazon.quivela.checker.Type type) {
        switch(type) {
            case Opaque:
                return BoogieType.Opaque;
            case Real :
                return BoogieType.Real;
            case Integer :
                return BoogieType.Integer;
            case Bitstring:
                return BoogieType.Bitstring;
            case Map:
                return BoogieType.Memory;
            case Expr :
                return BoogieType.Expr;
            default:
                throw new RuntimeException("type not supported: " + type);
        }
    }


    public static BoogieType getInType(String opStr, BoogieType left, BoogieType right) {
        if (opStr.equals("=")) {
            return BoogieType.Opaque;
        }
        else if (opStr.equals("==")) {
            if (left == BoogieType.Integer && right == BoogieType.Integer){
                return BoogieType.Integer;
            } else {
                return BoogieType.Bitstring;
            }
        } else if(opStr.equals("!=")) {
            if (left == BoogieType.Integer && right == BoogieType.Integer){
                return BoogieType.Integer;
            } else {
                return BoogieType.Bitstring;
            }
        } else if(opStr.equals("<=") || opStr.equals(">=") || opStr.equals("<") || opStr.equals(">")) {
            if (left == BoogieType.Real || right == BoogieType.Real) {
                return BoogieType.Real;
            } else {
                return BoogieType.Integer;
            }
        } else {
            throw new RuntimeException("unimplemented logic op: " + opStr);
        }

    }

    public static String convertBoolOp(String opStr) {
        if (opStr.equals("==") || opStr.equals("=")) {
            // Both use Boogie ==, but operand types are different
            return "==";
        } else if (opStr.equals("!=")) {
            return "!=";
        } else if(opStr.equals("<=")) {
            return "<=";
        } else if(opStr.equals(">=")) {
            return ">=";
        } else if(opStr.equals("<")) {
            return "<";
        } else if(opStr.equals(">")) {
            return ">";
        }else {
            throw new RuntimeException("unimplemented logic op: " + opStr);
        }
    }
}
