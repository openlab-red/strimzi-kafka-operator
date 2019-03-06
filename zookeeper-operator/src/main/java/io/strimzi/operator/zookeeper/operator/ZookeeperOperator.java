/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.List;
import java.util.function.Consumer;

public interface ZookeeperOperator<T extends CustomResource> {


    void createOrUpdate(Reconciliation reconciliation, T customResource, Handler<AsyncResult<Void>> handler);

    void reconcile(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler);

    void reconcileAll(String trigger, String namespace, Labels selector);

    Future<Watch> createWatch(String namespace, Labels selector, Consumer<KubernetesClientException> onClose);

    void delete(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler);

    List<HasMetadata> getResources(String namespace, Labels selector);

    int getPort();

    /**
     * Gets the name of the lock to be used for operating on the given {@code namespace} and
     * cluster {@code name}
     *
     * @param namespace The namespace containing the cluster
     * @param name      The name of the cluster
     * @param type      The Custom Resource of the cluster
     * @return String
     */
    default String getLockName(String namespace, String name, ResourceType type) {
        return "lock::" + namespace + "::" + type.name() + "::" + name;
    }


}
