/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;


import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackupSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.cluster.model.ClusterCa;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import io.strimzi.operator.zookeeper.utils.BatchUtils;
import io.strimzi.operator.zookeeper.utils.SecretUtils;
import io.strimzi.operator.zookeeper.utils.VolumeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZookeeperBackupModel implements ZookeeperModel<ZookeeperBackup> {
    private static final Logger log = LogManager.getLogger(ZookeeperBackupModel.class.getName());

    protected final String namespace;
    protected final String name;
    protected final Labels labels;
    protected PersistentVolumeClaim storage;
    protected Secret certSecret;
    protected CronJob cronJob;

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where Kafka Connect cluster resources are going to be created
     * @param name      Zookeeper Backup name
     * @param labels    Labels
     */
    public ZookeeperBackupModel(String namespace, String name, Labels labels) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels;
    }

    /**
     * Creates instance of ZookeeperBackupModel from CRD definition
     *
     * @param certManager     CertManager instance for work with certificates
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     * @param clusterCaCert   Secret with the Cluster CA cert
     * @param clusterCaCert   Secret with the Cluster CA key
     * @param certSecret      Secret with the current certificate
     * @return ZookeeperBackupModel
     */
    public static ZookeeperBackupModel fromCrd(CertManager certManager,
                                               ZookeeperBackup zookeeperBackup,
                                               Secret clusterCaCert,
                                               Secret clusterCaKey,
                                               Secret certSecret) {

        final String namespace = zookeeperBackup.getMetadata().getNamespace();
        final String name = zookeeperBackup.getMetadata().getName();
        final ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        final Labels labels = Labels.fromResource(zookeeperBackup).withKind(zookeeperBackup.getKind());


        ZookeeperBackupModel result = new ZookeeperBackupModel(namespace, name, labels);

        addStorage(zookeeperBackupSpec, result);

        addSecret(certManager, clusterCaCert, clusterCaKey, certSecret, result);

        addCronJob(result);

        return result;
    }

    /**
     * add Secret
     *
     * @param certManager   CertManager instance for work with certificates
     * @param clusterCaCert Secret with the Cluster CA cert
     * @param clusterCaCert Secret with the Cluster CA key
     * @param certSecret    Secret with the current certificate
     * @param result        ZookeeperBackupModel
     * @return ZookeeperBackupModel
     */
    private static void addSecret(CertManager certManager, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret, ZookeeperBackupModel result) {
        ClusterCa clusterCa = new ClusterCa(certManager,
            clusterCaCert.getMetadata().getName(),
            clusterCaCert,
            clusterCaKey,
            ZookeeperOperatorConfig.getClusterCaValidityDays(),
            ZookeeperOperatorConfig.getClusterCaRenewalDays(),
            false,
            null);

        certSecret = SecretUtils.buildSecret(clusterCa, certSecret, result.getNamespace(), result.getName(), Ca.IO_STRIMZI, ZookeeperOperatorConfig.ZOOKEEPER_BACKUP_CERT_NAME, result.getLabels(), null);

        result.setCertSecret(certSecret);

    }

    /**
     * add Storage
     *
     * @param zookeeperBackupSpec ZookeeperBackupSpec resources with the desired zookeeper backup configuration.
     * @param result              ZookeeperBackupModel
     */
    private static void addStorage(ZookeeperBackupSpec zookeeperBackupSpec, ZookeeperBackupModel result) {
        PersistentClaimStorage persistentClaimStorage;
        if (zookeeperBackupSpec.getStorage() instanceof PersistentClaimStorage) {
            persistentClaimStorage = (PersistentClaimStorage) zookeeperBackupSpec.getStorage();
            if (persistentClaimStorage.getSize() == null || persistentClaimStorage.getSize().isEmpty()) {
                throw new InvalidResourceException("The size is mandatory for a persistent-claim storage");
            }
        } else {
            throw new InvalidResourceException("Only persistent-claim storage type is supported");
        }

        result.setStorage(VolumeUtils.buildPersistentVolumeClaim(result.getName(), result.getNamespace(), result.getLabels(), persistentClaimStorage));
    }

    /**
     * add CronJob
     *
     * @param result ZookeeperBackupModel
     */
    private static void addCronJob(ZookeeperBackupModel result) {
        CronJob cronJob = BatchUtils.buildCronJob(result.getName(), result.getNamespace(), result.getLabels());
        result.setCronJob(cronJob);
    }

    public PersistentVolumeClaim getStorage() {
        return storage;
    }

    public void setStorage(PersistentVolumeClaim storage) {
        this.storage = storage;
    }

    public Secret getCertSecret() {
        return certSecret;
    }

    public void setCertSecret(Secret certSecret) {
        this.certSecret = certSecret;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public Labels getLabels() {
        return labels;
    }

    public CronJob getCronJob() {
        return cronJob;
    }

    public void setCronJob(CronJob cronJob) {
        this.cronJob = cronJob;
    }
}
