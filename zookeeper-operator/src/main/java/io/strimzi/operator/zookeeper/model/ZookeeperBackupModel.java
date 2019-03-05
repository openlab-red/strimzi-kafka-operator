/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;


import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackupSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.cluster.model.ClusterCa;
import io.strimzi.operator.common.exception.InvalidResourceException;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.utils.BatchUtils;
import io.strimzi.operator.common.utils.SecretUtils;
import io.strimzi.operator.common.utils.VolumeUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class ZookeeperBackupModel extends AbstractZookeeperModel<ZookeeperBackup> {
    private static final Logger log = LogManager.getLogger(ZookeeperBackupModel.class.getName());

    protected PersistentVolumeClaim storage;
    protected Secret secret;
    protected CronJob cronJob;
    protected Pod pod;

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

        certSecret = SecretUtils.buildSecret(clusterCa, certSecret, namespace,
            ZookeeperOperatorResources.secretBackupName(clusterName), Ca.IO_STRIMZI,
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_CERT_NAME,
            labels, null);

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
        setStorage(VolumeUtils.buildPersistentVolumeClaim(ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName),
            namespace, labels, persistentClaimStorage));
    }

    /**
     * add CronJob
     *
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     */
    @Override
    public void addCronJob(ZookeeperBackup zookeeperBackup) {
        final ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        final String schedule = zookeeperBackupSpec.getSchedule();
        final Boolean suspend = zookeeperBackupSpec.getSuspend();


        Container tlsSidecar = buildTlsSidecarContainer(zookeeperBackupSpec.getEndpoint());

        Container burry = buildBurryContainer("--endpoint=127.0.0.1:2181", "--target=local", "-b");

        CronJob cronJob = BatchUtils.buildCronJob(ZookeeperOperatorResources.cronJobsBackupName(clusterName),
            namespace, labels, schedule, suspend,
            Arrays.asList(tlsSidecar, burry),
            Arrays.asList(VolumeUtils.buildVolumePVC("volume-burry", ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("burry", ZookeeperOperatorResources.secretBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("cluster-ca", KafkaResources.clusterCaCertificateSecretName(clusterName)))
        );

        setCronJob(cronJob);
    }

    @Override
    public void addPod(ZookeeperBackup customResource) {


    }


    public void setPod(Pod pod) {
        this.pod = pod;
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
