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
public class ZookeeperBackupSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> additionalProperties;
    protected Storage storage;
    protected String endpoint;
    protected Retention retention;
    private String schedule;
    private Boolean suspend;

    /**
     * No args constructor for use in serialization
     *
     */
    public ZookeeperBackupSpec() {

    }

    /**
     *
     * @param additionalProperties Map String, Object
     * @param storage Storage
     * @param endpoint String
     * @param retention Retention
     * @param schedule String
     * @param suspend Boolean
     */
    public ZookeeperBackupSpec(Map<String, Object> additionalProperties, Storage storage, String endpoint, Retention retention, String schedule, Boolean suspend) {
        this.additionalProperties = additionalProperties;
        this.storage = storage;
        this.endpoint = endpoint;
        this.retention = retention;
        this.schedule = schedule;
        this.suspend = suspend;
    }

    @Description("Storage configuration (disk). Cannot be updated.")
    @JsonProperty(required = true)
    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Description("Zookeeper Endpoint configuration")
    @JsonProperty(required = true)
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Description("Retention disk configuration")
    @JsonProperty(required = true)
    public Retention getRetention() {
        return retention;
    }

    public void setRetention(Retention retention) {
        this.retention = retention;
    }


    @Description("CronJob scheduled")
    @JsonProperty(required = true)
    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }


    @Description("CronJob Suspend")
    public Boolean getSuspend() {
        return suspend;
    }

    public void setSuspend(Boolean suspend) {
        this.suspend = suspend;
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
