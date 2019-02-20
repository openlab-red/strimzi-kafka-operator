/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.DoneablePersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Operations for {@code PersistentVolumeClaim}s.
 */
public class PvcOperator extends AbstractResourceOperator<KubernetesClient, PersistentVolumeClaim, PersistentVolumeClaimList, DoneablePersistentVolumeClaim, Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim>> {
    /**
     * Constructor
     *
     * @param vertx  The Vertx instance
     * @param client The Kubernetes client
     */
    public PvcOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, "PersistentVolumeClaim");
    }

    @Override
    protected MixedOperation<PersistentVolumeClaim, PersistentVolumeClaimList, DoneablePersistentVolumeClaim, Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim>> operation() {
        return client.persistentVolumeClaims();
    }

    @Override
    public Future<ReconcileResult<PersistentVolumeClaim>> reconcile(String namespace, String name, PersistentVolumeClaim desired) {

        if (desired != null) {
            if (!namespace.equals(desired.getMetadata().getNamespace())) {
                return Future.failedFuture("Given namespace " + namespace + " incompatible with desired namespace " + desired.getMetadata().getNamespace());
            } else if (!name.equals(desired.getMetadata().getName())) {
                return Future.failedFuture("Given name " + name + " incompatible with desired name " + desired.getMetadata().getName());
            }
            PersistentVolumeClaim current = operation().inNamespace(namespace).withName(name).get();
            if (current != null) {
                log.debug("{} {}/{} already exists, ignoring, cannot patch persistent volume claim", resourceKind, namespace, name);
                return Future.succeededFuture();
            }
        }
        return super.reconcile(namespace, name, desired);
    }
}
