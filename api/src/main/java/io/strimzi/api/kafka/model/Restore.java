/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Representation of retention constraints
 */
@Buildable(
    editableEnabled = false,
    generateBuilderPackage = false,
    builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class Restore implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private boolean full;

    @Description("Restore type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Description("Restore method")
    public boolean isFull() {
        return full;
    }

    public void setFull(boolean full) {
        this.full = full;
    }
}
