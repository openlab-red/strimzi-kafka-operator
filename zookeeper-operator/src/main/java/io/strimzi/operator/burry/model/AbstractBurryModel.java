/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

import io.strimzi.operator.common.model.ImagePullPolicy;

/**
 *
 */
public abstract class AbstractBurryModel implements BurryModel {

    public static final String TLS_SIDECAR_CONTAINER_NAME = "tls-sidecar";
    public static final String BURRY_CONTAINER_NAME = "burry";
    public static final String BURRYFEST_FILENAME = ".burryfest";

    public static final String BURRY_TLS_SIDECAR_VOLUME_NAME = "burry";
    public static final String BURRY_CLUSTER_CA_VOLUME_NAME = "cluster-ca";
    public static final String BURRY_BACKUP_VOLUME_NAME = "volume-burry";
    public static final String BURRYFEST_VOLUME_NAME = "burryfest";

    protected final ImagePullPolicy imagePullPolicy;
    protected final String clusterName;


    /**
     * Default Constructor of BurryModel
     *
     * @param imagePullPolicy Image Pull Policy
     * @param clusterName     Name of the kafka cluster
     */
    public AbstractBurryModel(ImagePullPolicy imagePullPolicy, String clusterName) {
        this.imagePullPolicy = imagePullPolicy;
        this.clusterName = clusterName;
    }
}
