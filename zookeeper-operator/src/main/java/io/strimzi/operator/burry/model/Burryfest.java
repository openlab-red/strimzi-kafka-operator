/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Buildable(
    editableEnabled = false,
    generateBuilderPackage = false,
    builderPackage = "io.fabric8.kubernetes.api.builder"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class Burryfest implements Serializable {

    @JsonProperty("svc")
    private String svc;
    @JsonProperty("svc-endpoint")
    private String svcEndpoint;
    @JsonProperty("timeout")
    private Integer timeout;
    @JsonProperty("target")
    private String target;
    @JsonProperty("credentials")
    private Credentials credentials;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    private final static long serialVersionUID = -8460596751055519974L;

    @JsonProperty("svc")
    public String getSvc() {
        return svc;
    }

    @JsonProperty("svc")
    public void setSvc(String svc) {
        this.svc = svc;
    }

    @JsonProperty("svc-endpoint")
    public String getSvcEndpoint() {
        return svcEndpoint;
    }

    @JsonProperty("svc-endpoint")
    public void setSvcEndpoint(String svcEndpoint) {
        this.svcEndpoint = svcEndpoint;
    }

    @JsonProperty("timeout")
    public Integer getTimeout() {
        return timeout;
    }

    @JsonProperty("timeout")
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    @JsonProperty("target")
    public String getTarget() {
        return target;
    }

    @JsonProperty("target")
    public void setTarget(String target) {
        this.target = target;
    }

    @JsonProperty("credentials")
    public Credentials getCredentials() {
        return credentials;
    }

    @JsonProperty("credentials")
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
