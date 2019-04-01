/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.model.BatchModel;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.StandardModel;

public abstract class AbstractZookeeperModel<T extends CustomResource> implements StandardModel<T>, BatchModel<T> {

    protected final String namespace;
    protected final String name;
    protected final Labels labels;
    protected final String clusterName;
    protected final ImagePullPolicy imagePullPolicy;

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name      Zookeeper Backup name
     * @param labels    Labels
     */
    public AbstractZookeeperModel(String namespace, String name, Labels labels, ImagePullPolicy imagePullPolicy) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels.withName(name);
        this.clusterName = clusterName();
        this.imagePullPolicy = imagePullPolicy;
    }

    /**
     * Return cluster name
     *
     * @return String
     */
    private String clusterName() {
        return labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);
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

}
