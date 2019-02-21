/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.certs.CertManager;

public interface Model<T extends CustomResource> {

    String getName();

    String getNamespace();

    Labels getLabels();

    void fromCrd(CertManager certManager, T customResource, Secret caCert, Secret caKey, Secret certSecret);
}
