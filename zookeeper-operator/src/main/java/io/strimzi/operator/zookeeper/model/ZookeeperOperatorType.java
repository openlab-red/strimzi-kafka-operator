/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

public enum ZookeeperOperatorType {

    BACKUP("backup"),
    RESTORE("restore");

    private final String type;

    ZookeeperOperatorType(String type) {
        this.type = type;
    }

    public String toString() {
        return type;
    }

}
