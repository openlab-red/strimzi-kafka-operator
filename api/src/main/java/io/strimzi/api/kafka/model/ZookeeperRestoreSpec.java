/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

@Buildable(
    editableEnabled = false,
    generateBuilderPackage = false,
    builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class ZookeeperRestoreSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> additionalProperties;

    protected Restore restore;
    protected String endpoint;
    protected Snapshot snapshot;

    @Description("Restore configuration.")
    @JsonProperty(required = true)
    public Restore getRestore() {
        return restore;
    }

    public void setRestore(Restore restore) {
        this.restore = restore;
    }

    @Description("Zookeeper Endpoint configuration")
    @JsonProperty(required = true)
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }


    @Description("Snapshot configuration")
    @JsonProperty(required = true)
    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties != null ? this.additionalProperties : emptyMap();
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        if (this.additionalProperties == null) {
            this.additionalProperties = new HashMap<>();
        }
        this.additionalProperties.put(name, value);
    }
}
