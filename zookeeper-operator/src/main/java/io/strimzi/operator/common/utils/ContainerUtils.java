/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.common.utils;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.strimzi.operator.cluster.model.ImagePullPolicy;

import java.util.List;

public class ContainerUtils {

    private ContainerUtils() {
    }


    public static Container addContainer(String name, String image, List<EnvVar> envVars, ImagePullPolicy imagePullPolicy, List<VolumeMount> volumes, String terminationMessagePath, String... args) {

        ContainerBuilder containerBuilder = new ContainerBuilder()
            .withName(name)
            .withEnv(envVars)
            .withImagePullPolicy(imagePullPolicy.toString())
            .withImage(image)
            .withArgs(args)
            .withVolumeMounts(volumes)
            .withTerminationMessagePath(terminationMessagePath);

        return containerBuilder.build();

    }
}
