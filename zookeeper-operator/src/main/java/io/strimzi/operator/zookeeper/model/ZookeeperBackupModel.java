/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;


import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackupSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.cluster.model.ClusterCa;
import io.strimzi.operator.cluster.model.ImagePullPolicy;
import io.strimzi.operator.common.exception.InvalidResourceException;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.utils.BatchUtils;
import io.strimzi.operator.common.utils.ContainerUtils;
import io.strimzi.operator.common.utils.EnvVarUtils;
import io.strimzi.operator.common.utils.SecretUtils;
import io.strimzi.operator.common.utils.VolumeUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ZookeeperBackupModel extends AbstractZookeeperModel<ZookeeperBackup> {
    private static final Logger log = LogManager.getLogger(ZookeeperBackupModel.class.getName());

    protected PersistentVolumeClaim storage;
    protected Secret secret;
    protected CronJob cronJob;

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name      Zookeeper Backup name
     * @param labels    Labels
     */
    public ZookeeperBackupModel(String namespace, String name, Labels labels) {
        super(namespace, name, labels);
    }

    /**
     * Creates instance of ZookeeperBackupModel from CRD definition
     *
     * @param certManager     CertManager instance for work with certificates
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     * @param clusterCaCert   Secret with the Cluster CA cert
     * @param clusterCaKey    Secret with the Cluster CA key
     * @param certSecret      Secret with the current certificate
     */
    @Override
    public void fromCrd(CertManager certManager, ZookeeperBackup zookeeperBackup, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret) {

        addStorage(zookeeperBackup);

        addSecret(certManager, clusterCaCert, clusterCaKey, certSecret);

        addCronJob(zookeeperBackup);

    }

    /**
     * add Secret
     *
     * @param certManager   CertManager instance for work with certificates
     * @param clusterCaCert Secret with the Cluster CA cert
     * @param clusterCaKey  Secret with the Cluster CA key
     * @param certSecret    Secret with the current certificate
     */
    @Override
    public void addSecret(CertManager certManager, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret) {
        ClusterCa clusterCa = new ClusterCa(certManager,
            clusterCaCert.getMetadata().getName(),
            clusterCaCert,
            clusterCaKey,
            ZookeeperOperatorConfig.getClusterCaValidityDays(),
            ZookeeperOperatorConfig.getClusterCaRenewalDays(),
            false,
            null);

        certSecret = SecretUtils.buildSecret(clusterCa, certSecret, namespace, name, Ca.IO_STRIMZI, ZookeeperOperatorConfig.ZOOKEEPER_BACKUP_CERT_NAME, labels, null);

        setSecret(certSecret);

    }

    /**
     * add Storage
     *
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     */
    @Override
    public void addStorage(ZookeeperBackup zookeeperBackup) {
        ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        PersistentClaimStorage persistentClaimStorage;
        if (zookeeperBackupSpec.getStorage() instanceof PersistentClaimStorage) {
            persistentClaimStorage = (PersistentClaimStorage) zookeeperBackupSpec.getStorage();
            if (persistentClaimStorage.getSize() == null || persistentClaimStorage.getSize().isEmpty()) {
                throw new InvalidResourceException("The size is mandatory for a persistent-claim storage");
            }
        } else {
            throw new InvalidResourceException("Only persistent-claim storage type is supported");
        }
        setStorage(VolumeUtils.buildPersistentVolumeClaim(name, namespace, labels, persistentClaimStorage));
    }

    /**
     * add CronJob
     *
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     */
    @Override
    public void addCronJob(ZookeeperBackup zookeeperBackup) {
        ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        final Map<String, String> map = zookeeperBackup.getMetadata().getLabels();

        List<EnvVar> envVarList = Arrays.asList(EnvVarUtils.buildEnvVar("KAFKA_ZOOKEEPER_CONNECT", zookeeperBackupSpec.getEndpoint()),
            EnvVarUtils.buildEnvVar("TLS_SIDECAR_LOG_LEVEL", ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_LOG_LEVEL),
            EnvVarUtils.buildEnvVar("KAFKA_CERTS_NAME", ZookeeperOperatorConfig.ZOOKEEPER_BACKUP_CERT_NAME));


        Container tlsSidecar = ContainerUtils.addContainer("tls-sidecar",
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_BURRY_IMAGE, envVarList,
            ImagePullPolicy.ALWAYS,
            Arrays.asList(VolumeUtils.buildVolumeMount("burry", "/etc/tls-sidecar/burry/"),
                VolumeUtils.buildVolumeMount("cluster-ca", "/etc/tls-sidecar/cluster-ca-certs/"))
        );

        Container burry = ContainerUtils.addContainer("burry",
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_BURRY_IMAGE,
            null,
            ImagePullPolicy.ALWAYS,
            Arrays.asList(VolumeUtils.buildVolumeMount("volume-burry", "/home/burry")),
            "--endpoint=127.0.0.1:2181", "--target=local", "-b");

        CronJob cronJob = BatchUtils.buildCronJob(name, namespace, labels, zookeeperBackupSpec.getSchedule(),
            Arrays.asList(tlsSidecar, burry),
            Arrays.asList(VolumeUtils.buildVolumePVC("volume-burry", name),
                VolumeUtils.buildVolumeSecret("burry", name),
                VolumeUtils.buildVolumeSecret("cluster-ca", map.get(Labels.STRIMZI_CLUSTER_LABEL) + "-cluster-ca-cert"))
        );

        setCronJob(cronJob);
    }


    @Override
    public PersistentVolumeClaim getStorage() {
        return storage;
    }

    public void setStorage(PersistentVolumeClaim storage) {
        this.storage = storage;
    }

    @Override
    public Secret getSecret() {
        return secret;
    }

    public void setSecret(Secret secret) {
        this.secret = secret;
    }

    @Override
    public CronJob getCronJob() {
        return cronJob;
    }

    public void setCronJob(CronJob cronJob) {
        this.cronJob = cronJob;
    }

}
