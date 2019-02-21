/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

/**
 * Enum for ConcurrencyPolicy types. Supports the 3 types supported in Kubernetes / OpenShift:
 * - Allow
 * - Forbid
 * - Replace
 */
public enum ConcurrencyPolicy {
    ALLOW("Allow"),
    FORBID("Forbid"),
    REPLACE("Replace");

    private final String concurrencyPolicy;

    ConcurrencyPolicy(String concurrencyPolicy) {
        this.concurrencyPolicy = concurrencyPolicy;
    }

    public String toString() {
        return concurrencyPolicy;
    }
}
