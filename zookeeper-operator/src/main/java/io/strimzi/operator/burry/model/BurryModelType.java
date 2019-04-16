/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

public enum BurryModelType {

    S3("s3"),
    PERSISTENT_CLAIM("persistent-claim");

    private final String type;

    BurryModelType(String type) {
        this.type = type;
    }

    public String toString() {
        return type;
    }



}
