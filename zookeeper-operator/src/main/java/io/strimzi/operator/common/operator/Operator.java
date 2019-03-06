/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.strimzi.operator.common.Reconciliation;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public interface Operator<T extends HasMetadata> {


    void reconcile(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler);

    CountDownLatch reconcileAll(String trigger, String namespace);

    Future<Watch> createWatch(String watchNamespace, Consumer<KubernetesClientException> onClose);

    int getPort();

}
