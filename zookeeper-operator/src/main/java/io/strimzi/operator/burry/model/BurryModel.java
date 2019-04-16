/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;

public interface BurryModel {

    Container getTlsSidecar(String endpoint);

    Container getBurry(String... args);

    PodSpec getPodSpec(String endpoint, String... args);
}
