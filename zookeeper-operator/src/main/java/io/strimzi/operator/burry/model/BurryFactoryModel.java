/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

import io.strimzi.operator.common.model.ImagePullPolicy;

import java.util.Locale;

/**
 *
 */
public class BurryFactoryModel {

    /**
     * Create Burry Model based on type
     *
     * @param type            burry model type
     * @param imagePullPolicy Image Policy
     * @return Burry Model
     */
    protected static BurryModel create(BurryModelType type, ImagePullPolicy imagePullPolicy, String clusterName) {

        switch (type) {
            case S3:
                return new S3BurryModel(imagePullPolicy, clusterName);
            case PERSISTENT_CLAIM:
                return new LocalBurryModel(imagePullPolicy, clusterName);
            default:
                return null;
        }

    }

    /**
     * Create Burry Model based on type
     *
     * @param type            burry model type
     * @param imagePullPolicy Image Policy
     * @return Burry Model
     */
    public static BurryModel create(String type, ImagePullPolicy imagePullPolicy, String clusterName) {

        type = type.toUpperCase(Locale.getDefault()).replace("-", "_");
        return create(BurryModelType.valueOf(type.toUpperCase(Locale.getDefault()).replace("-", "_")), imagePullPolicy, clusterName);
    }
}
