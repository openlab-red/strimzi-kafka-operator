/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.CustomResource;

public interface AppsModel<T extends CustomResource> extends Model<T> {

    void addStatefulSet(T customResource);

    StatefulSet getStatefulSet();

}
