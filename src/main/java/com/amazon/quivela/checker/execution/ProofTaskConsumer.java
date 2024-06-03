/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela.checker.execution;

public interface ProofTaskConsumer {
    void add(ProofTask task);
}
