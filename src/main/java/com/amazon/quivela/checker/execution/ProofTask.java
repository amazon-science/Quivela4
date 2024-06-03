/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.execution;

import com.amazon.quivela.checker.CheckException;

import java.util.Optional;

public interface ProofTask {

    enum Status{
        NOT_STARTED,
        RUNNING,
        COMPLETE
    }

    void start(int workerId);
    Status getStatus();
    Optional<CheckException> getException();
    String getTaskMessage();
    double getPriority();
}
