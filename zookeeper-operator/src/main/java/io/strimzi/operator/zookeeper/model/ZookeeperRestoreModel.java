/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.api.kafka.model.ZookeeperRestoreSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.burry.model.BurryModel;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.common.model.ClusterCa;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.operator.resource.SimpleStatefulSetOperator;
import io.strimzi.operator.common.utils.BatchUtils;
import io.strimzi.operator.common.utils.SecretUtils;
import io.strimzi.operator.common.utils.VolumeUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

public class ZookeeperRestoreModel extends AbstractZookeeperModel<ZookeeperRestore> {
    private static final Logger log = LogManager.getLogger(ZookeeperRestoreModel.class.getName());

    protected Secret secret;
    protected Job job;
    protected StatefulSet statefulSet;
    protected final SimpleStatefulSetOperator statefulSetOperator;

    /**
     * Constructor
     *
     * @param namespace           Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name                Zookeeper Restore name
     * @param labels              Labels
     * @param statefulSetOperator SimpleStatefulSetOperator
     */
    public ZookeeperRestoreModel(String namespace, String name, Labels labels, SimpleStatefulSetOperator statefulSetOperator) {
        super(namespace, name, labels);
        this.statefulSetOperator = statefulSetOperator;
    }

    /**
     * Creates instance of ZookeeperRestoreModel from CRD definition
     *
     * @param certManager      CertManager instance for work with certificates
     * @param zookeeperRestore ZookeeperRestore resources with the desired zookeeper restore configuration.
     * @param clusterCaCert    Secret with the Cluster CA cert
     * @param clusterCaKey     Secret with the Cluster CA key
     * @param certSecret       Secret with the current certificate
     */
    @Override
    public void fromCrd(CertManager certManager, ZookeeperRestore zookeeperRestore, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret) {

        addSecret(certManager, clusterCaCert, clusterCaKey, certSecret);

        addJob(zookeeperRestore);

        addStatefulSet(zookeeperRestore);


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
            ZookeeperOperatorResources.secretRestoreName(clusterName), Ca.IO_STRIMZI,
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_CERT_NAME,
            labels, null);

        setSecret(certSecret);

    }

    /**
     * add Job
     *
     * @param zookeeperRestore ZookeeperRestore resources with the desired zookeeper restore configuration.
     */
    @Override
    public void addJob(ZookeeperRestore zookeeperRestore) {
        ZookeeperRestoreSpec zookeeperRestoreSpec = zookeeperRestore.getSpec();
        final Map<String, String> map = zookeeperRestore.getMetadata().getLabels();
        final String endpoint = zookeeperRestoreSpec.getEndpoint();
        final String snapshotId = zookeeperRestoreSpec.getSnapshot().getId();
        final String clusterName = map.get(Labels.STRIMZI_CLUSTER_LABEL);
        final BurryModel burryModel = new BurryModel(endpoint, "--operation=restore", "--endpoint=127.0.0.1:2181", "--target=local", "--snapshot=" + snapshotId);


        Job job = BatchUtils.buildJob(ZookeeperOperatorResources.jobsRestoreName(clusterName, snapshotId),
            namespace, labels, Arrays.asList(burryModel.getTlsSidecar(), burryModel.getBurry()),
            Arrays.asList(VolumeUtils.buildVolumePVC("volume-burry",
                ZookeeperOperatorResources.persistentVolumeClaimBackupName(clusterName)),
                VolumeUtils.buildVolumeSecret("burry", ZookeeperOperatorResources.secretRestoreName(clusterName)),
                VolumeUtils.buildVolumeSecret("cluster-ca", KafkaResources.clusterCaCertificateSecretName(clusterName))));

        setJob(job);

    }


    /**
     * addStatefulSet
     *
     * @param zookeeperRestore ZookeeperRestore resources with the desired zookeeper restore configuration.
     */
    @Override
    public void addStatefulSet(ZookeeperRestore zookeeperRestore) {
        setStatefulSet(statefulSetOperator.get(namespace, KafkaResources.zookeeperStatefulSetName(clusterName)));
    }

    @Override
    public StatefulSet getStatefulSet() {
        return statefulSet;
    }

    public void setStatefulSet(StatefulSet statefulSet) {
        this.statefulSet = statefulSet;
    }

    @Override
    public Secret getSecret() {
        return secret;
    }

    public void setSecret(Secret secret) {
        this.secret = secret;
    }

    @Override
    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

}
