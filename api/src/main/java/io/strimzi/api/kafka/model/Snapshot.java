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
public class Snapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String path;

    @Description("This determines the name of the backup archive")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    @Description("This determines where to get the backup archive")
    public String gePath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }


}
