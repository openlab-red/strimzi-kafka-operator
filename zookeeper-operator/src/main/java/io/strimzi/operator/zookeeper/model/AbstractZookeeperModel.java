/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.cluster.model.ImagePullPolicy;
import io.strimzi.operator.common.model.AppsModel;
import io.strimzi.operator.common.model.BatchModel;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.utils.ContainerUtils;
import io.strimzi.operator.common.utils.EnvVarUtils;
import io.strimzi.operator.common.utils.VolumeUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractZookeeperModel<T extends CustomResource> implements BatchModel<T>, AppsModel<T> {

    protected final String namespace;
    protected final String name;
    protected final Labels labels;

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name      Zookeeper Backup name
     * @param labels    Labels
     */
    public AbstractZookeeperModel(String namespace, String name, Labels labels) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels;
    }

    /**
     * TODO: move in dedicate class
     * Build TlsSidecarContainer
     *
     * @param endpoint String Zookeeper endpoint
     * @return Container
     */
    protected Container buildTlsSidecarContainer(String endpoint) {
        List<EnvVar> envVarList = Arrays.asList(EnvVarUtils.buildEnvVar("KAFKA_ZOOKEEPER_CONNECT", endpoint),
            EnvVarUtils.buildEnvVar("TLS_SIDECAR_LOG_LEVEL", ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_LOG_LEVEL),
            EnvVarUtils.buildEnvVar("KAFKA_CERTS_NAME", ZookeeperOperatorConfig.ZOOKEEPER_BACKUP_CERT_NAME));


        return ContainerUtils.addContainer("tls-sidecar",
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_BURRY_IMAGE, envVarList,
            ImagePullPolicy.ALWAYS,
            Arrays.asList(VolumeUtils.buildVolumeMount("burry", "/etc/tls-sidecar/burry/"),
                VolumeUtils.buildVolumeMount("cluster-ca", "/etc/tls-sidecar/cluster-ca-certs/"),
                VolumeUtils.buildVolumeMount("volume-burry", "/home/burry")),
            "/dev/termination-log");
    }

    /**
     * TODO: move in dedicate class
     *
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


    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Labels getLabels() {
        return labels;
    }

    @Override
    public void addCronJob(T customResource) {

    }

    @Override
    public void addJob(T customResource) {

    }

    @Override
    public void addSecret(CertManager certManager, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret) {

    }

    @Override
    public void addStorage(T customResource) {

    }

    @Override
    public void addStatefulSet(T customResource) {

    }

    @Override
    public CronJob getCronJob() {
        return null;
    }


    @Override
    public Job getJob() {
        return null;
    }


    @Override
    public PersistentVolumeClaim getStorage() {
        return null;
    }

    @Override
    public Secret getSecret() {
        return null;
    }

    @Override
    public StatefulSet getStatefulSet() {
        return null;
    }
}
