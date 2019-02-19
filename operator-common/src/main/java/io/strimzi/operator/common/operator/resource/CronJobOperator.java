/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobList;
import io.fabric8.kubernetes.api.model.batch.DoneableCronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.vertx.core.Vertx;

/**
 * Operations for {@code CronJob}s.
 */
public class CronJobOperator extends AbstractResourceOperator<KubernetesClient, CronJob, CronJobList, DoneableCronJob, Resource<CronJob, DoneableCronJob>> {
    /**
     * Constructor
     * @param vertx The Vertx instance
     * @param client The Kubernetes client
     */
    public CronJobOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, "CronJob");
    }

    @Override
    protected MixedOperation<CronJob, CronJobList, DoneableCronJob, Resource<CronJob, DoneableCronJob>> operation() {
        return client.batch().cronjobs();
    }
}
