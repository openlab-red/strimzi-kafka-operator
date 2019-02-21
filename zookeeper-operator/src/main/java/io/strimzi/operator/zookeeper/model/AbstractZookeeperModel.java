/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.model.BatchModel;
import io.strimzi.operator.common.model.Labels;

public abstract class AbstractZookeeperModel<T extends CustomResource> implements BatchModel<T> {

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
    public CronJob getCronJob() {
        return null;
    }

    @Override
    public void addJob(T customResource) {

    }

    @Override
    public Job getJob() {
        return null;
    }

    @Override
    public void addSecret(CertManager certManager, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret) {

    }

    @Override
    public void addStorage(T customResource) {

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
