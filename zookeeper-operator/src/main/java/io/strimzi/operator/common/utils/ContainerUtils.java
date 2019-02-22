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


    /**
     * add Container
     * @param name  name of the container
     * @param image container image
     * @param envVars List of the environment variable
     * @param imagePullPolicy ImagePullPolicy
     * @param volumes List of mounted volumes
     * @param terminationMessagePath termination Message Path
     * @param args container arguments
     * @return Container
     */
    public static Container addContainer(String name, String image, List<EnvVar> envVars, ImagePullPolicy imagePullPolicy, List<VolumeMount> volumes, String terminationMessagePath, String... args) {
        return ContainerUtils.createContainerBuilder(name, image, envVars, imagePullPolicy, volumes, terminationMessagePath, args).build();

    }


    /**
     * createContainerBuilder
     * @param name  name of the container
     * @param image container image
     * @param envVars List of the environment variable
     * @param imagePullPolicy ImagePullPolicy
     * @param volumes List of mounted volumes
     * @param terminationMessagePath termination Message Path
     * @param args container arguments
     * @return ContainerBuilder
     */
    public static ContainerBuilder createContainerBuilder(String name, String image, List<EnvVar> envVars, ImagePullPolicy imagePullPolicy, List<VolumeMount> volumes, String terminationMessagePath, String... args) {
        return new ContainerBuilder()
            .withName(name)
            .withImage(image)
            .withEnv(envVars)
            .withImagePullPolicy(imagePullPolicy.toString())
            .withVolumeMounts(volumes)
            .withTerminationMessagePath(terminationMessagePath)
            .withArgs(args);
    }
}
