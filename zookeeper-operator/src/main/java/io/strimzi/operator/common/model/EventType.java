/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

/**
 * Enum for Event kubernetes types. Supports the 3 types supported in Kubernetes / OpenShift:
 * - Never
 * - OnFailure
 */
public enum EventType {
    NORMAL("Normal"),
    WARNING("Warning");

    private final String eventType;

    EventType(String eventType) {
        this.eventType = eventType;
    }

    public String toString() {
        return eventType;
    }
}
