/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import io.fabric8.kubernetes.api.model.Secret;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.api.kafka.model.ZookeeperRestoreSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.burry.model.BurryFactoryModel;
import io.strimzi.operator.burry.model.BurryModel;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.common.model.ClusterCa;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.utils.BatchUtils;
import io.strimzi.operator.common.utils.SecretUtils;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZookeeperRestoreModel extends AbstractZookeeperModel<ZookeeperRestore> {
    private static final Logger log = LogManager.getLogger(ZookeeperRestoreModel.class.getName());

    protected final CronJobOperator cronJobOperator;

    /**
     * Constructor
     *
     * @param namespace       Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name            Zookeeper Restore name
     * @param labels          Labels
     * @param secretOperator  SecretOperator to mange secret resources
     * @param cronJobOperator CronJobOperator
     */
    public ZookeeperRestoreModel(String namespace, String name, Labels labels, SecretOperator secretOperator, CronJobOperator cronJobOperator, ImagePullPolicy imagePullPolicy) {
        super(namespace, name, labels, imagePullPolicy, secretOperator);
        this.cronJobOperator = cronJobOperator;
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

        addNetworkPolicy(zookeeperRestore);

        addSecret(certManager, clusterCaCert, clusterCaKey, certSecret);

        addConfig(zookeeperRestore);

        addJob(zookeeperRestore);

        addCronJob(zookeeperRestore);


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

        this.secret = SecretUtils.buildSecret(clusterCa, certSecret, namespace,
            ZookeeperOperatorResources.secretRestoreName(clusterName), Ca.IO_STRIMZI,
            ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_CERT_NAME,
            labels, null);


    }


    /**
     * add Job
     *
     * @param zookeeperRestore ZookeeperRestore resources with the desired zookeeper restore configuration.
     */
    @Override
    public void addJob(ZookeeperRestore zookeeperRestore) {
        final ZookeeperRestoreSpec zookeeperRestoreSpec = zookeeperRestore.getSpec();
        final String endpoint = zookeeperRestoreSpec.getEndpoint();
        final Integer snapshotId = zookeeperRestoreSpec.getSnapshot().getId();
        final String type = zookeeperRestoreSpec.getRestore().getType();

        final BurryModel burryModel = BurryFactoryModel.create(type, imagePullPolicy, clusterName);


        this.job = BatchUtils.buildJob(
            ZookeeperOperatorResources.jobsRestoreName(clusterName, snapshotId),
            namespace, labels,
            burryModel.getPodSpec(endpoint, ZookeeperOperatorResources.secretRestoreName(clusterName),
                "--operation=restore", "--snapshot=" + snapshotId, "--reject=/strimzi"));

    }

    @Override
    public void addCronJob(ZookeeperRestore zookeeperRestore) {
        this.cronJob = cronJobOperator.get(namespace, ZookeeperOperatorResources.cronJobsBackupName(clusterName));
    }

    @Override
    protected String getBurryStorageType(ZookeeperRestore zookeeperRestore) {
        return zookeeperRestore.getSpec().getRestore().getType();
    }

}
