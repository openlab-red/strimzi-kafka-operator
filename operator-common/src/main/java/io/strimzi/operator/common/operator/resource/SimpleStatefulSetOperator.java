/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.apps.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;

/**
 * Operations for {@code StatefulSet}s.
 */
public class SimpleStatefulSetOperator extends AbstractScalableResourceOperator<KubernetesClient, StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> {

    private final PodOperator podOperations;

    /**
     * Constructor
     *
     * @param vertx  The Vertx instance
     * @param client The Kubernetes client
     */
    public SimpleStatefulSetOperator(Vertx vertx, KubernetesClient client) {
        this(vertx, client, new PodOperator(vertx, client));
    }

    public SimpleStatefulSetOperator(Vertx vertx, KubernetesClient client, PodOperator podOperations) {
        super(vertx, client, "StatefulSet");
        this.podOperations = podOperations;
    }

    @Override
    protected MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> operation() {
        return client.apps().statefulSets();
    }

    @Override
    protected Integer currentScale(String namespace, String name) {
        StatefulSet deployment = get(namespace, name);
        if (deployment != null) {
            return deployment.getSpec().getReplicas();
        } else {
            return null;
        }
    }

    /**
     * Returns a future that completes when all the pods [0..replicas-1] in the given statefulSet are ready.
     *
     * @param namespace          String namespace of the stateful set
     * @param resource           StatefulSet resource
     * @param pollInterval       long poll interval
     * @param operationTimeoutMs long operation timeout
     * @return Future
     */
    public Future<?> podReadiness(String namespace, StatefulSet resource, long pollInterval, long operationTimeoutMs) {
        final int replicas = resource.getSpec().getReplicas();
        List<Future> waitPodResult = new ArrayList<>(replicas);
        for (int i = 0; i < replicas; i++) {
            String podName = getPodName(resource, i);
            waitPodResult.add(podOperations.readiness(namespace, podName, pollInterval, operationTimeoutMs));
        }
        return CompositeFuture.join(waitPodResult);
    }

    /**
     * Return PodName
     *
     * @param resource StatefulSet resource
     * @param podId    pod Id
     * @return
     */
    public static String getPodName(StatefulSet resource, int podId) {
        return resource.getSpec().getTemplate().getMetadata().getName() + "-" + podId;
    }
}
