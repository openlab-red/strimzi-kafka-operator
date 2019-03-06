/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.DoneableEvent;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.operator.common.model.Labels;
import io.vertx.core.Vertx;

/**
 * Operations for {@code Event}s
 */
public class EventOperator extends AbstractResourceOperator<KubernetesClient, Event, EventList, DoneableEvent, Resource<Event, DoneableEvent>> {
    /**
     * Constructor
     *
     * @param vertx  The Vertx instance
     * @param client The Kubernetes client
     */
    public EventOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, "Events");
    }

    @Override
    protected MixedOperation<Event, EventList, DoneableEvent, Resource<Event, io.fabric8.kubernetes.api.model.DoneableEvent>> operation() {
        return client.events();
    }

    public void createEvent(String namespace, Event event) {
        log.debug("{} {}/{} Creating event", resourceKind, namespace, event.getMetadata().getName());
        client.events().inNamespace(namespace).create(event);
    }


    public Watch watch(String namespace, Labels selector, Watcher<Event> watcher) {
        return operation().inNamespace(namespace).withLabels(selector.toMap()).watch(watcher);
    }

}
