/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.certs.CertManager;

public interface AppModel<T extends CustomResource> extends Model<T> {

    void addSecret(CertManager certManager, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret);

    void addStorage(T customResource);

    PersistentVolumeClaim getStorage();

    Secret getSecret();




}
