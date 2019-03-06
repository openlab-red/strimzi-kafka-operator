/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.utils.ContainerUtils;
import io.strimzi.operator.common.utils.EnvVarUtils;
import io.strimzi.operator.common.utils.VolumeUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;

import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class BurryModel {

    private final Container tlsSidecar;
    private final Container burry;


    public BurryModel(String endpoint, String... args) {
        this.tlsSidecar = buildTlsSidecarContainer(endpoint);
        this.burry = buildBurryContainer(args);
    }

    public Container getTlsSidecar() {
        return tlsSidecar;
    }

    public Container getBurry() {
        return burry;
    }

    /**
     * Build TlsSidecarContainer
     *
     * @param endpoint String Zookeeper endpoint
     * @return Container
     */
    protected Container buildTlsSidecarContainer(String endpoint) {
        List<EnvVar> envVarList = Arrays.asList(EnvVarUtils.buildEnvVar("KAFKA_ZOOKEEPER_CONNECT", endpoint),
            EnvVarUtils.buildEnvVar("TLS_SIDECAR_LOG_LEVEL", ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_LOG_LEVEL),
            EnvVarUtils.buildEnvVar("KAFKA_CERTS_NAME", ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_CERT_NAME));


        return ContainerUtils.addContainer("tls-sidecar",
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_BURRY_IMAGE, envVarList,
            ImagePullPolicy.ALWAYS,
            Arrays.asList(VolumeUtils.buildVolumeMount("burry", "/etc/tls-sidecar/burry/"),
                VolumeUtils.buildVolumeMount("cluster-ca", "/etc/tls-sidecar/cluster-ca-certs/"),
                VolumeUtils.buildVolumeMount("volume-burry", "/home/burry")),
            "/dev/termination-log");
    }

    /**
     * @param args Container argument
     * @return
     */
    protected Container buildBurryContainer(String... args) {
        return ContainerUtils.addContainer("burry",
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_BURRY_IMAGE,
            null,
            ImagePullPolicy.ALWAYS,
            Arrays.asList(VolumeUtils.buildVolumeMount("volume-burry", "/home/burry")),
            "/dev/termination-log",
            args);
    }
}
