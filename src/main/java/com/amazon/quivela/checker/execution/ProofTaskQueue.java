/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.execution;

import com.amazon.quivela.checker.CheckException;

import java.time.LocalDateTime;
import java.util.*;

public class ProofTaskQueue implements ProofTaskConsumer {

    private List<ProofTask> tasks = new ArrayList();
    private final List<ProofTaskWorker> workers = new ArrayList();
    private long lastStatusTime = -1;

    private static class ProofTaskWorker {

        private final int workerId;
        private ProofTask task = null;

        public ProofTaskWorker(int workerId) {
            this.workerId = workerId;
        }

        public void reset() {
            this.task = null;
        }
        public void setTask(ProofTask task) {
            this.task = task;
        }
        public boolean isRunning() {
            return task!=null && task.getStatus() == ProofTask.Status.RUNNING;
        }
        public boolean isComplete() {
            return task!=null && task.getStatus() == ProofTask.Status.COMPLETE;
        }

        private ProofTask getTask() {
            return task;
        }
        public boolean tryStartTask(ProofTask task) {
            if (getTask() == null) {
                setTask(task);
                task.start(workerId);
                return true;
            }
            return false;
        }

        public Optional<CheckException> getCheckException() {
            return task.getException();
        }

        public void printStatus() {
            System.out.print("[" + workerId + "]: ");
            if (!isRunning()) {
                System.out.println("Waiting");
            } else {
                System.out.println(task.getTaskMessage());
            }
        }
    }

    public ProofTaskQueue() {
        int numWorkers = Runtime.getRuntime().availableProcessors();
        for(int i = 0; i < numWorkers; i++) {
            ProofTaskWorker worker = new ProofTaskWorker(i);
            workers.add(worker);
        }
    }

    private void completeTasks() throws CheckException {
        for(ProofTaskWorker curWorker : workers) {
            if (curWorker.isComplete()) {
                System.out.println("[" + curWorker.workerId + "] Task complete: " + curWorker.getTask().getTaskMessage());
                if (curWorker.getCheckException().isPresent()) {
                    throw(curWorker.getCheckException().get());
                }

                curWorker.reset();
            }
        }
    }

    private void scheduleTask(ProofTask task) throws CheckException {
        while(true) {

            tryPrintTaskStatus();

            completeTasks();

            for(ProofTaskWorker curWorker : workers) {
                if (curWorker.tryStartTask(task)) {
                    return;
                }
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                // do nothing
            }
        }
    }

    private void printTaskStatus() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("[" + now.getHour() + ":" + now.getMinute() + ":" + now.getSecond() + "] Proof worker status:");
        System.out.println("-------------------------------------------------------------");
        for(ProofTaskWorker curWorker : workers) {
            curWorker.printStatus();
        }
        System.out.println("-------------------------------------------------------------");
    }

    private void tryPrintTaskStatus() {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastStatusTime;
        if (timeDiff > 3000) {
            printTaskStatus();
            lastStatusTime = currentTime;
        }
    }

    public void checkTasks() throws CheckException {

        tasks.sort((t1, t2) -> (t1.getPriority() < t2.getPriority() ? -1 : t1.getPriority() > t2.getPriority() ? 1 : 0));

        while (!tasks.isEmpty()) {
            scheduleTask(tasks.remove(0));
        }

        boolean running = true;
        while(running) {
            running = false;
            for(ProofTaskWorker curWorker : workers) {
                running |= curWorker.isRunning();
            }

            tryPrintTaskStatus();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                // do nothing
            }

            completeTasks();
        }

        System.out.println("Proof tasks completed");

    }

    @Override
    public void add(ProofTask task) {
        tasks.add(task);
    }
}
