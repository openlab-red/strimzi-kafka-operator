/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.utils;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.strimzi.operator.common.model.EventType;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class EventUtils {

    private EventUtils() {
    }

    public static Event createEvent(String namespace, String name, EventType type, String message, String reason, String component, HasMetadata resource) {

        return createEventBuilder(namespace, name, type, message, reason, component, resource.getMetadata().getLabels())
            .withNewInvolvedObject()
            .withKind(resource.getKind())
            .withName(resource.getMetadata().getName())
            .withApiVersion(resource.getApiVersion())
            .withNamespace(resource.getMetadata().getNamespace())
            .withUid(resource.getMetadata().getUid())
            .endInvolvedObject().build();
    }

    public static Event createEvent(String namespace, String name, EventType type, String message, String reason, String component, Map<String, String> labels) {
        return createEventBuilder(namespace, name, type, message, reason, component, labels).build();
    }

    protected static EventBuilder createEventBuilder(String namespace, String name, EventType type, String message, String reason, String component, Map<String, String> labels) {

        //2006-01-02T15:04:05Z07:00
        final String eventTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss'Z'"));

        return new EventBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels)
            .withCreationTimestamp(eventTime)
            .endMetadata()
            .withFirstTimestamp(eventTime)
            .withLastTimestamp(eventTime)// Used by Monitoring page
            .withType(type.toString())
            .withMessage(message)
            .withReason(reason)
            .withNewSource()
            .withComponent(component)
            .endSource();
    }

}
