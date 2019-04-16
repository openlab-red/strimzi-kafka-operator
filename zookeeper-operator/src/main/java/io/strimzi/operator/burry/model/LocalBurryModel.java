/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.burry.model;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.utils.BatchUtils;
import io.strimzi.operator.common.utils.ContainerUtils;
import io.strimzi.operator.common.utils.EnvVarUtils;
import io.strimzi.operator.common.utils.VolumeUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import io.strimzi.operator.zookeeper.model.ZookeeperOperatorResources;

import java.util.Arrays;
import java.util.List;

public class LocalBurryModel extends AbstractBurryModel {

    public LocalBurryModel(ImagePullPolicy imagePullPolicy, String clusterName) {
        super(imagePullPolicy, clusterName);
    }

    /**
     * Build TlsSidecar Container
     *
     * @param endpoint String Zookeeper endpoint
     * @return Container
     */
    @Override
    public Container getTlsSidecar(String endpoint) {
        List<EnvVar> envVarList = Arrays.asList(
            EnvVarUtils.buildEnvVar("KAFKA_ZOOKEEPER_CONNECT", endpoint),
            EnvVarUtils.buildEnvVar("TLS_SIDECAR_LOG_LEVEL", ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_LOG_LEVEL),
            EnvVarUtils.buildEnvVar("KAFKA_CERTS_NAME", ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_CERT_NAME));


        return ContainerUtils.addContainer(TLS_SIDECAR_CONTAINER_NAME,
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_BURRY_IMAGE, envVarList,
            imagePullPolicy,
            Arrays.asList(
                VolumeUtils.buildVolumeMount(BURRY_TLS_SIDECAR_VOLUME_NAME, "/etc/tls-sidecar/burry/"),
                VolumeUtils.buildVolumeMount(BURRY_CLUSTER_CA_VOLUME_NAME, "/etc/tls-sidecar/cluster-ca-certs/"),
                VolumeUtils.buildVolumeMount(BURRY_BACKUP_VOLUME_NAME, "/home/burry")),
            "/dev/termination-log", null);
    }

    /**
     * Build Burry container
     *
     * @param args Container argument
     * @return Container
     */
    @Override
    public Container getBurry(String... args) {
        return ContainerUtils.addContainer(BURRY_CONTAINER_NAME,
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_BURRY_IMAGE,
            null,
            imagePullPolicy,
            Arrays.asList(
                VolumeUtils.buildVolumeMount(BURRY_BACKUP_VOLUME_NAME, "/home/burry"),
                VolumeUtils.buildVolumeMount(BURRYFEST_VOLUME_NAME, "/home/burry/.burryfest", ".burryfest")),
            "/dev/termination-log",
            Arrays.asList(args));
    }

    /**
     * Create Pod Spec for burry
     *
     * @param endpoint   zookeeper endpoint
     * @param secretName secret name which contains the certificate for tls sidecar
     * @param args       burry args
     * @return Pod of burry using peristent volume
     */
    @Override
    public PodSpec getPodSpec(String endpoint, String secretName, String... args) {

        return BatchUtils.buildPodSpec(
            Arrays.asList(
                this.getTlsSidecar(endpoint),
                this.getBurry(args)),
            Arrays.asList(
                VolumeUtils.buildVolumePVC(BURRY_BACKUP_VOLUME_NAME, ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret(BURRY_TLS_SIDECAR_VOLUME_NAME, secretName),
                VolumeUtils.buildVolumeSecret(BURRY_CLUSTER_CA_VOLUME_NAME, KafkaResources.clusterCaCertificateSecretName(clusterName)),
                VolumeUtils.buildVolumeSecret(BURRYFEST_VOLUME_NAME, ZookeeperOperatorResources.burrySecretManifestName(clusterName, BurryModelType.PERSISTENT_CLAIM.toString()))));
    }
}
