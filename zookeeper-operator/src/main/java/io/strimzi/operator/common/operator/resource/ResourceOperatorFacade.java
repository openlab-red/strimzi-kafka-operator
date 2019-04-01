/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.vertx.core.Vertx;

public class ResourceOperatorFacade {

    private final SecretOperator secretOperator;
    private final PvcOperator pvcOperator;
    private final CronJobOperator cronJobOperator;
    private final PodOperator podOperator;
    private final EventOperator eventOperator;
    private final JobOperator jobOperator;
    private final SimpleStatefulSetOperator statefulSetOperator;
    private final NetworkPolicyOperator networkPolicyOperator;


    /**
     * @param vertx  The Vertx instance
     * @param client KubernetesClient
     */
    public ResourceOperatorFacade(Vertx vertx, KubernetesClient client) {
        this.secretOperator = new SecretOperator(vertx, client);
        this.pvcOperator = new PvcOperator(vertx, client);
        this.cronJobOperator = new CronJobOperator(vertx, client);
        this.jobOperator = new JobOperator(vertx, client);
        this.podOperator = new PodOperator(vertx, client);
        this.eventOperator = new EventOperator(vertx, client);
        this.statefulSetOperator = new SimpleStatefulSetOperator(vertx, client);
        this.networkPolicyOperator = new NetworkPolicyOperator(vertx, client);
    }

    public SecretOperator getSecretOperator() {
        return secretOperator;
    }

    public PvcOperator getPvcOperator() {
        return pvcOperator;
    }

    public CronJobOperator getCronJobOperator() {
        return cronJobOperator;
    }

    public PodOperator getPodOperator() {
        return podOperator;
    }

    public EventOperator getEventOperator() {
        return eventOperator;
    }

    public JobOperator getJobOperator() {
        return jobOperator;
    }

    public SimpleStatefulSetOperator getStatefulSetOperator() {
        return statefulSetOperator;
    }

    public NetworkPolicyOperator getNetworkPolicyOperator() {
        return networkPolicyOperator;
    }
}
