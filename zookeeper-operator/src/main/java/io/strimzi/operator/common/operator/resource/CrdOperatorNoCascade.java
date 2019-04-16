/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.Crds;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class CrdOperatorNoCascade<C extends KubernetesClient,
    T extends CustomResource,
    L extends CustomResourceList<T>,
    D extends Doneable<T>>
    extends CrdOperator<C, T, L, D> {

    /**
     * Constructor
     *
     * @param vertx       The Vertx instance
     * @param client      The Kubernetes client
     * @param cls         Resource class
     * @param listCls     List of resource class
     * @param doneableCls doneable resource class
     */
    public CrdOperatorNoCascade(Vertx vertx, C client, Class<T> cls, Class<L> listCls, Class<D> doneableCls) {
        super(vertx, client, cls, listCls, doneableCls);
    }

    @Override
    protected MixedOperation<T, L, D, Resource<T, D>> operation() {
        return Crds.operationCascading(client, cls, listCls, doneableCls);
    }

    // Currently Crd do not support cascade
    @Override
    protected Future<ReconcileResult<T>> internalDelete(String namespace, String name) {
        try {
            log.debug("Override {} {} in namespace {} has been deleted", resourceKind, name, namespace);
            operation().inNamespace(namespace).withName(name).delete();
            log.debug("{} {} in namespace {} has been deleted", resourceKind, name, namespace);
            return Future.succeededFuture(ReconcileResult.deleted());
        } catch (Exception e) {
            log.debug("Caught exception while deleting {} {} in namespace {}", resourceKind, name, namespace, e);
            return Future.failedFuture(e);
        }
    }
}
