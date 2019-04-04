/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;


import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.api.kafka.model.S3Storage;
import io.strimzi.api.kafka.model.Schedule;
import io.strimzi.api.kafka.model.Storage;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackupSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.burry.model.BurryModel;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.common.exception.InvalidResourceException;
import io.strimzi.operator.common.model.ClusterCa;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.operator.resource.SecretOperator;
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
    protected Job job;
    protected Pod pod;
    protected SecretOperator secretOperator;

    /**
     * Constructor
     *
     * @param namespace      Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name           Zookeeper Backup name
     * @param labels         Labels
     * @param secretOperator SecretOperator to mange secret resources
     */
    public ZookeeperBackupModel(String namespace, String name, Labels labels, ImagePullPolicy imagePullPolicy, SecretOperator secretOperator) {
        super(namespace, name, labels, imagePullPolicy);

        this.secretOperator = secretOperator;
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

        addNetworkPolicy(zookeeperBackup);

        addStorage(zookeeperBackup);

        addSecret(certManager, clusterCaCert, clusterCaKey, certSecret);

        final Schedule schedule = zookeeperBackup.getSpec().getSchedule();

        if (schedule.isAdhoc()) {
            addJob(zookeeperBackup);
        } else {
            addCronJob(zookeeperBackup);
        }

    }

    /**
     * add Secret
     * TODO: in case of existing secret don't create again.
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

        this.secret = SecretUtils.buildSecret(clusterCa, certSecret, namespace,
            ZookeeperOperatorResources.secretBackupName(clusterName), Ca.IO_STRIMZI,
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_CERT_NAME,
            labels, null);
    }

    /**
     * add Storage
     *
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     */
    @Override
    public void addStorage(ZookeeperBackup zookeeperBackup) {
        ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        final String type = zookeeperBackupSpec.getStorage().getType();
        log.info("{} type of storage", type);
        if (Storage.TYPE_PERSISTENT_CLAIM.equalsIgnoreCase(type)) {
            PersistentClaimStorage persistentClaimStorage = (PersistentClaimStorage) zookeeperBackupSpec.getStorage();
            if (persistentClaimStorage.getSize() == null || persistentClaimStorage.getSize().isEmpty()) {
                throw new InvalidResourceException("The size is mandatory for a persistent-claim storage");
            }
            this.storage = VolumeUtils.buildPersistentVolumeClaim(ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName),
                namespace, labels, persistentClaimStorage);
        } else if (Storage.TYPE_S3.equalsIgnoreCase(type)) {
            S3Storage s3Storage = (S3Storage) zookeeperBackupSpec.getStorage();
            final String credentials = s3Storage.getCredentials();
            if (credentials == null || credentials.isEmpty()) {
                throw new InvalidResourceException("The credentials secret name is mandatory for a s3 storage");
            }
            if (this.secretOperator.get(namespace, credentials) == null) {
                throw new InvalidResourceException("The secret " + credentials + " does not exist on namespace" + namespace);
            }
        } else {
            throw new InvalidResourceException("Only persistent-claim storage type is supported");
        }

    }

    /**
     * add CronJob
     *
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     */
    @Override
    public void addCronJob(ZookeeperBackup zookeeperBackup) {
        final ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        final String schedule = zookeeperBackupSpec.getSchedule().getCron();
        final Boolean suspend = zookeeperBackupSpec.getSuspend();
        final String endpoint = zookeeperBackupSpec.getEndpoint();

        final BurryModel burryModel = new BurryModel(imagePullPolicy, endpoint, "--endpoint=127.0.0.1:2181", "--target=local", "-b");

        this.cronJob = BatchUtils.buildCronJob(ZookeeperOperatorResources.cronJobsBackupName(clusterName),
            namespace, labels, schedule, suspend,
            Arrays.asList(burryModel.getTlsSidecar(), burryModel.getBurry()),
            Arrays.asList(VolumeUtils.buildVolumePVC("volume-burry", ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("burry", ZookeeperOperatorResources.secretBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("cluster-ca", KafkaResources.clusterCaCertificateSecretName(clusterName)))
        );

    }

    /**
     * add Job
     *
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper restore configuration.
     */
    @Override
    public void addJob(ZookeeperBackup zookeeperBackup) {
        ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        final String endpoint = zookeeperBackupSpec.getEndpoint();
        final BurryModel burryModel = new BurryModel(imagePullPolicy, endpoint, "--endpoint=127.0.0.1:2181", "--target=local", "-b");


        this.job = BatchUtils.buildJob(ZookeeperOperatorResources.jobsBackupAdHocName(clusterName),
            namespace, labels, Arrays.asList(burryModel.getTlsSidecar(), burryModel.getBurry()),
            Arrays.asList(VolumeUtils.buildVolumePVC("volume-burry",
                ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("burry", ZookeeperOperatorResources.secretBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("cluster-ca", KafkaResources.clusterCaCertificateSecretName(clusterName))));

    }


    @Override
    public PersistentVolumeClaim getStorage() {
        return storage;
    }

    @Override
    public Secret getSecret() {
        return secret;
    }

    @Override
    public CronJob getCronJob() {
        return cronJob;
    }

    @Override
    public Job getJob() {
        return job;
    }

}
