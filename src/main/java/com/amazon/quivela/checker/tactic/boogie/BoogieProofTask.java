/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.tactic.boogie;

import com.amazon.quivela.checker.CheckException;
import com.amazon.quivela.checker.execution.ProofTask;

import java.io.*;
import java.util.Optional;

public class BoogieProofTask implements ProofTask {

    private final File file;
    private final int line;
    private final int pos;
    private final String boogieProgram;
    private final String message;
    private final String failMessage;
    private ProofTask.Status status = Status.NOT_STARTED;
    InputStream procInputStream = null;
    Process boogieProcess = null;
    CheckException exception = null;
    StringBuffer procOutBuf = new StringBuffer();
    byte[] cbuf = new byte[1024];
    private int workerId = 0;
    private double priority;

    public BoogieProofTask(File file, int line, int pos, String boogieProgram, String message, String failMessage, double priority) {
        this.file = file;
        this.line = line;
        this.pos = pos;
        this.boogieProgram = boogieProgram;
        this.message = message;
        this.failMessage = failMessage;
        this.priority = priority;
    }

    @Override
    public void start(int workerId)  {

        if (status != Status.NOT_STARTED) {
            throw new IllegalStateException("Task already started");
        }
        status = Status.RUNNING;
        this.workerId = workerId;

        try {
            if(BoogieUtil.isCached(boogieProgram)) {
                status = Status.COMPLETE;
            } else {
                boogieProcess = BoogieUtil.initVerify(workerId, boogieProgram);
                procInputStream = boogieProcess.getInputStream();
            }
        } catch (IOException ex) {
            status = Status.COMPLETE;
            exception = new CheckException(ex);
        }
    }

    private void checkResult() {
        String procOutStr = procOutBuf.toString();
        if (BoogieUtil.boogieOutSuccess(procOutStr)) {
            try {
                BoogieUtil.cache(boogieProgram);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            System.err.println(procOutStr);
            exception = new CheckException(file, line, pos, failMessage + " Try analyzing " + BoogieUtil.getBoogieFile(workerId) + " using boogie.");
        }
    }

    private void update() {

        if (procInputStream == null) {
            return;
        }

        try {
            while (procInputStream.available() > 0) {
                int numRead = procInputStream.read(cbuf, 0, cbuf.length);
                procOutBuf.append(new String(cbuf, 0, numRead));
            }
            if (!boogieProcess.isAlive()) {
                status = Status.COMPLETE;
                checkResult();
            }
        } catch (IOException ex) {
            status = Status.COMPLETE;
            exception = new CheckException(ex);
        }
    }

    @Override
    public double getPriority() {
        return priority;
    }

    @Override
    public Status getStatus() {

        update();

        return status;
    }

    @Override
    public Optional<CheckException> getException() {
        return Optional.ofNullable(exception);
    }

    @Override
    public String getTaskMessage() {
        return (message + " at " + file.getName() +"(" + line + ":" + pos + ")");
    }
}
