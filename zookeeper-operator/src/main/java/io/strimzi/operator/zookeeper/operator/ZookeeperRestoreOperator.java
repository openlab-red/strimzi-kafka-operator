/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.ZookeeperRestoreList;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.DoneableZookeeperRestore;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.api.kafka.model.ZookeeperRestoreSpec;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.AbstractBaseOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.JobOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.operator.resource.SimpleStatefulSetOperator;
import io.strimzi.operator.zookeeper.model.ZookeeperOperatorResources;
import io.strimzi.operator.zookeeper.model.ZookeeperRestoreModel;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Operator for a Zookeeper Restore.
 */
public class ZookeeperRestoreOperator extends AbstractBaseOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore, Resource<ZookeeperRestore, DoneableZookeeperRestore>> {

    private static final Logger log = LogManager.getLogger(ZookeeperRestoreOperator.class.getName());
    private final CrdOperator crdBackupOperator;
    private final SecretOperator secretOperations;
    private final PvcOperator pvcOperator;
    private final JobOperator jobOperator;
    private final SimpleStatefulSetOperator statefulSetOperator;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
    private static final int HEALTH_SERVER_PORT = 8082;

    /**
     * @param vertx               The Vertx instance
     * @param assemblyType        The resource type
     * @param certManager         For managing certificates
     * @param resourceOperator    For operating on Custom Resources
     * @param crdBackupOperator   For operating on Zookeeper Backup Custom Resources
     * @param secretOperations    For operating on Secrets
     * @param pvcOperator         For operating on Persistent Volume Claim
     * @param jobOperator         For operating on Job
     * @param statefulSetOperator For operating on StatefulSet
     * @param caCertName          The name of the Secret containing the cluster CA certificate
     * @param caKeyName           The name of the Secret containing the cluster CA private key
     * @param caNamespace         The namespace of the Secret containing the cluster CA
     */
    public ZookeeperRestoreOperator(Vertx vertx,
                                    ResourceType assemblyType,
                                    CertManager certManager,
                                    CrdOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore> resourceOperator,
                                    CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdBackupOperator,
                                    SecretOperator secretOperations,
                                    PvcOperator pvcOperator,
                                    JobOperator jobOperator,
                                    SimpleStatefulSetOperator statefulSetOperator,
                                    String caCertName, String caKeyName, String caNamespace) {

        super(vertx, assemblyType, certManager, resourceOperator);
        this.secretOperations = secretOperations;
        this.pvcOperator = pvcOperator;
        this.jobOperator = jobOperator;
        this.statefulSetOperator = statefulSetOperator;
        this.crdBackupOperator = crdBackupOperator;
        this.caCertName = caCertName;
        this.caKeyName = caKeyName;
        this.caNamespace = caNamespace;
    }

    /**
     * Creates or updates the zookeeper restore. The implementation
     * should not assume that any resources are in any particular state (e.g. that the absence on
     * one resource means that all resources need to be created).
     *
     * @param reconciliation   Unique identification for the reconciliation
     * @param zookeeperRestore ZookeeperRestore resources with the desired zookeeper restore configuration.
     */

    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, ZookeeperRestore zookeeperRestore) {
        final String namespace = reconciliation.namespace();
        final String name = reconciliation.name();
        final Labels labels = Labels.fromResource(zookeeperRestore).withKind(zookeeperRestore.getKind());
        final String clusterName = labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);
        final Secret clusterCaCert = secretOperations.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperations.get(caNamespace, caKeyName);
        final Secret restoreSecret = secretOperations.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        final Future<Void> chain = Future.future();
        ZookeeperRestoreModel zookeeperRestoreModel;
        try {
            zookeeperRestoreModel = new ZookeeperRestoreModel(namespace, name, labels, statefulSetOperator);
            zookeeperRestoreModel.fromCrd(certManager, zookeeperRestore, clusterCaCert, clusterCaKey, restoreSecret);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        Secret desired = zookeeperRestoreModel.getSecret();
        Job desiredJob = zookeeperRestoreModel.getJob();
        StatefulSet desiredStatefulSet = zookeeperRestoreModel.getStatefulSet();

        // Job are immutable, this should always empty operation unless using the same snapshot over and over
        jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), null).compose(res -> {
            if (zookeeperRestore.getSpec().getRestore().isFull()) {
                return CompositeFuture.join(
                    secretOperations.reconcile(namespace, desired.getMetadata().getName(), desired),
                    // TODO: full restore
                    // pvcOperator.reconcile(namespace, KafkaResources.z)
                    // statefulSetOperator.scaleDown(namespace, desiredStatefulSet.getMetadata().getName(), 0),
                    // statefulSetOperator.podReadiness(namespace, desiredStatefulSet, 1_000, 2_000),
                    // TODO: wait kafka
                    jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob)
                    //TODO: watch status of the jobs
                );
            } else {
                return CompositeFuture.join(
                    secretOperations.reconcile(namespace, desired.getMetadata().getName(), desired),
                    jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob)
                );
            }
        }).compose(state -> chain.complete(), chain);

        log.debug("{}: Updating ZookeeperRestore {} in namespace {}", reconciliation, name, namespace);

        return chain;

    }


    /**
     * Deletes the zookeeper restore
     * Previous Jobs are kept for history.
     *
     * @param reconciliation Reconciliation
     */
    @Override
    protected Future<Void> delete(Reconciliation reconciliation) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        log.debug("{}: Deleting ZookeeperRestore", reconciliation, name, namespace);

        // TODO: is it necessary to remove the secret each time ?
        return secretOperations.reconcile(namespace, ZookeeperOperatorResources.secretRestoreName(name), null).map((Void) null);
    }


    /**
     * Suspend the backup before proceed
     * TODO: NOT WORKING due ZookeeperBackup use CRD Operation and not HasMetadataOperation
     * So to suspend the backup I have to suspend the cronjob.
     *
     * @param zookeeperRestore ZookeeperRestore Custom Resource
     * @param namespace        The Namespace
     * @param suspend          The suspend flag
     * @param handler          handler
     */
    @SuppressWarnings("unchecked")
    protected void suspendBackup(Reconciliation reconciliation, ZookeeperRestore zookeeperRestore, String namespace, Boolean suspend, Handler<AsyncResult<Void>> handler) {
        final ZookeeperRestoreSpec zookeeperRestoreSpec = zookeeperRestore.getSpec();
        final String zookeeperBackupName = zookeeperRestoreSpec.getZookeeperBackup();

        log.info("{}: {} should be suspended: {}", reconciliation, zookeeperBackupName, suspend);

        ZookeeperBackup zookeeperBackup = (ZookeeperBackup) crdBackupOperator.get(namespace, zookeeperRestoreSpec.getZookeeperBackup());
        zookeeperBackup.getSpec().setSuspend(suspend);

        final Future reconcile = crdBackupOperator.reconcile(namespace, zookeeperBackup.getMetadata().getName(), zookeeperBackup);
        if (handler != null) {
            reconcile.map((Void) null).setHandler(handler);
        }
    }

    /**
     * Gets all resources relevant to ZookeeperRestore
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return List
     */
    @Override
    public List<HasMetadata> getResources(String namespace, Labels selector) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public int getPort() {
        return HEALTH_SERVER_PORT;
    }
}
