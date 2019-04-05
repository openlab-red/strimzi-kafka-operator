/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.crdgenerator.annotations.Crd;
import io.strimzi.crdgenerator.annotations.Description;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

@JsonDeserialize(
    using = JsonDeserializer.None.class
)
@Crd(
    apiVersion = ZookeeperBackup.CRD_API_VERSION,
    spec = @Crd.Spec(
        names = @Crd.Spec.Names(
            kind = ZookeeperBackup.RESOURCE_KIND,
            plural = ZookeeperBackup.RESOURCE_PLURAL,
            shortNames = {ZookeeperBackup.SHORT_NAME}
        ),
        group = ZookeeperBackup.RESOURCE_GROUP,
        scope = ZookeeperBackup.SCOPE,
        version = ZookeeperBackup.VERSION
    )
)
@Buildable(
    editableEnabled = false,
    generateBuilderPackage = false,
    builderPackage = "io.fabric8.kubernetes.api.builder",
    inline = @Inline(type = Doneable.class, prefix = "Doneable", value = "done")
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
@EqualsAndHashCode
public class ZookeeperBackup extends CustomResource implements io.strimzi.api.kafka.model.UnknownPropertyPreserving {
    private static final long serialVersionUID = 1L;

    public static final String SCOPE = "Namespaced";
    public static final String VERSION = "v1alpha1";
    public static final String RESOURCE_KIND = "ZookeeperBackup";
    public static final String RESOURCE_LIST_KIND = RESOURCE_KIND + "List";
    public static final String RESOURCE_GROUP = "kafka.strimzi.io";
    public static final String RESOURCE_PLURAL = "zookeeperbackups";
    public static final String RESOURCE_SINGULAR = "zookeeperbackup";
    public static final String CRD_API_VERSION = "apiextensions.k8s.io/v1beta1";
    public static final String CRD_NAME = RESOURCE_PLURAL + "." + RESOURCE_GROUP;
    public static final String SHORT_NAME = "zkb";
    public static final List<String> RESOURCE_SHORTNAMES = singletonList(SHORT_NAME);

    private String apiVersion;
    private ObjectMeta metadata;
    private ZookeeperBackupSpec spec;
    private Map<String, Object> additionalProperties = new HashMap<>(0);


    /**
     * No args constructor for use in serialization
     */
    public ZookeeperBackup() {
    }

    /**
     * @param apiVersion
     * @param metadata
     * @param spec
     * @param additionalProperties
     */
    public ZookeeperBackup(String apiVersion, ObjectMeta metadata, ZookeeperBackupSpec spec, Map<String, Object> additionalProperties) {
        this.apiVersion = apiVersion;
        this.metadata = metadata;
        this.spec = spec;
        this.additionalProperties = additionalProperties;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    public ObjectMeta getMetadata() {
        return super.getMetadata();
    }

    @Override
    public void setMetadata(ObjectMeta metadata) {
        super.setMetadata(metadata);
    }

    @Description("The specification of the zookeeper backup.")
    public ZookeeperBackupSpec getSpec() {
        return spec;
    }

    public void setSpec(ZookeeperBackupSpec spec) {
        this.spec = spec;
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

    @Override
    public String toString() {
        YAMLMapper mapper = new YAMLMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
