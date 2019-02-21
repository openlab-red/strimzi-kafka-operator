/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

/**
 * Enum for RestartPolicy types. Supports the 3 types supported in Kubernetes / OpenShift:
 * - Never
 * - OnFailure
 */
public enum RestartPolicy {
    NEVER("Never"),
    ONFAILURE("OnFailure");

    private final String restartPolicy;

    RestartPolicy(String restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    public String toString() {
        return restartPolicy;
    }
}
