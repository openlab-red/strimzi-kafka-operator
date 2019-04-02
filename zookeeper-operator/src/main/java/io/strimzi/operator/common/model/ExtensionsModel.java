/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.client.CustomResource;

public interface ExtensionsModel<T extends CustomResource> {

    void addNetworkPolicy(T customResource);

    NetworkPolicy getNetworkPolicy();

}


