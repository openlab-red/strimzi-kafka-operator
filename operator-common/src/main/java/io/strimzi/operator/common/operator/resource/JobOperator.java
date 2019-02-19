/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;


import io.fabric8.kubernetes.api.model.batch.DoneableJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.vertx.core.Vertx;

/**
 * Operations for {@code Job}s.
 */
public class JobOperator extends AbstractResourceOperator<KubernetesClient, Job, JobList, DoneableJob, ScalableResource<Job, DoneableJob>> {
    /**
     * Constructor
     * @param vertx The Vertx instance
     * @param client The Kubernetes client
     */
    public JobOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, "Job");
    }

    @Override
    protected MixedOperation<Job, JobList, DoneableJob, ScalableResource<Job, DoneableJob>> operation() {
        return client.batch().jobs();
    }
}
