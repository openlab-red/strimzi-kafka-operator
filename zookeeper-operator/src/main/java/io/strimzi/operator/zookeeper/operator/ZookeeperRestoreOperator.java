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
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
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
import io.vertx.core.shareddata.Lock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Operator for a Zookeeper Restore.
 */
public class ZookeeperRestoreOperator implements ZookeeperOperator<ZookeeperRestore> {
    private static final Logger log = LogManager.getLogger(ZookeeperRestoreOperator.class.getName());
    private static final int LOCK_TIMEOUT_MS = 10;
    private static final String RESOURCE_KIND = "ZookeeperRestore";
    private final Vertx vertx;
    private final CrdOperator crdOperator;
    private final CrdOperator crdBackupOperator;
    private final SecretOperator secretOperations;
    private final PvcOperator pvcOperator;
    private final JobOperator jobOperator;
    private final SimpleStatefulSetOperator statefulSetOperator;
    private final CertManager certManager;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
    private static final int HEALTH_SERVER_PORT = 8082;

    /**
     * @param vertx               The Vertx instance
     * @param certManager         For managing certificates
     * @param crdOperator         For operating on Zookeeper Restore Custom Resources
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
                                    CertManager certManager,
                                    CrdOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore> crdOperator,
                                    CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdBackupOperator,
                                    SecretOperator secretOperations,
                                    PvcOperator pvcOperator,
                                    JobOperator jobOperator,
                                    SimpleStatefulSetOperator statefulSetOperator,
                                    String caCertName, String caKeyName, String caNamespace) {
        this.vertx = vertx;
        this.certManager = certManager;
        this.secretOperations = secretOperations;
        this.pvcOperator = pvcOperator;
        this.jobOperator = jobOperator;
        this.statefulSetOperator = statefulSetOperator;
        this.crdOperator = crdOperator;
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
     * @param handler          Completion handler
     */
    @Override
    @SuppressWarnings("unchecked")
    public void createOrUpdate(Reconciliation reconciliation, ZookeeperRestore zookeeperRestore, Handler<AsyncResult<Void>> handler) {
        final String namespace = reconciliation.namespace();
        final String name = reconciliation.name();
        final Labels labels = Labels.fromResource(zookeeperRestore).withKind(zookeeperRestore.getKind());
        final String clusterName = labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);
        Secret clusterCaCert = secretOperations.get(caNamespace, caCertName);
        Secret clusterCaKey = secretOperations.get(caNamespace, caKeyName);
        Secret restoreSecret = secretOperations.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        ZookeeperRestoreModel zookeeperRestoreModel;
        try {
            zookeeperRestoreModel = new ZookeeperRestoreModel(namespace, name, labels, statefulSetOperator);
            zookeeperRestoreModel.fromCrd(certManager, zookeeperRestore, clusterCaCert, clusterCaKey, restoreSecret);
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
            return;
        }

        Secret desired = zookeeperRestoreModel.getSecret();
        Job desiredJob = zookeeperRestoreModel.getJob();
        StatefulSet desiredStatefulSet = zookeeperRestoreModel.getStatefulSet();

        // Job are immutable, this should always empty operation unless using the same snapshot over and over
        jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), null).compose(res ->
            CompositeFuture.join(
                secretOperations.reconcile(namespace, desired.getMetadata().getName(), desired),
                // TODO: full restore
                // pvcOperator.reconcile ... all zookeeper volume
                // statefulSetOperator.scaleDown(namespace, desiredStatefulSet.getMetadata().getName(), 0),
                // statefulSetOperator.podReadiness(namespace, desiredStatefulSet, 1_000, 2_000),
                // TODO: wait kafka
                jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob)
                //TODO: watch status of the jobs
            )).map((Void) null).setHandler(handler);

        log.debug("{}: Updating ZookeeperRestore {} in namespace {}", reconciliation, name, namespace);

    }


    /**
     * Deletes the zookeeper restore
     * Previous Jobs are kept for history.
     *
     * @param reconciliation Reconciliation
     * @param handler        Completion handler
     */
    @Override
    public void delete(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        log.debug("{}: Deleting ZookeeperRestore", reconciliation, name, namespace);

        // TODO: is it necessary to remove the secret each time ?
        secretOperations.reconcile(namespace, ZookeeperOperatorResources.secretRestoreName(name), null).map((Void) null).setHandler(handler);
    }

    /**
     * Reconcile assembly resources in the given namespace having the given {@code name}.
     * Reconciliation works by getting the assembly resource (e.g. {@code ZookeeperRestore}) in the given namespace with the given name and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     *
     * @param reconciliation Reconciliation
     * @param handler        Completion handler
     */
    @Override
    public final void reconcile(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        final String lockName = getLockName(namespace, name, ResourceType.ZOOKEEPERRESTORE);

        vertx.sharedData().getLockWithTimeout(lockName, LOCK_TIMEOUT_MS, res -> {
            if (res.succeeded()) {
                log.debug("{}: Lock {} acquired", reconciliation, lockName);
                Lock lock = res.result();

                try {
                    ZookeeperRestore cr = (ZookeeperRestore) crdOperator.get(namespace, name);
                    if (cr != null) {
                        suspendBackup(reconciliation, cr, namespace, true, suspendResult -> {
                            log.info("{}: Job {} should be created or updated", reconciliation, name);

                            createOrUpdate(reconciliation, cr, createResult -> {
                                lock.release();
                                log.debug("{}: Lock {} released", reconciliation, lockName);
                                if (createResult.failed()) {
                                    log.error("{}: createOrUpdate failed", reconciliation, createResult.cause());
                                } else {
                                    crdOperator.reconcile(namespace, name, null).compose(crdOperatorRes -> {
                                        suspendBackup(reconciliation, cr, namespace, false, null);
                                        handler.handle(createResult);
                                        return Future.succeededFuture();
                                    });

                                }
                            });
                        });
                    } else {
                        log.info("{}: ZookeeperRestore {} should be deleted", reconciliation, name);
                        delete(reconciliation, deleteResult -> {
                            if (deleteResult.succeeded()) {
                                log.info("{}: ZookeeperRestore {} deleted", reconciliation, name);
                                lock.release();
                                log.debug("{}: ZookeeperRestore {} released", reconciliation, lockName);
                                handler.handle(deleteResult);
                            } else {
                                log.error("{}: Deletion of ZookeeperRestore {} failed", reconciliation, name, deleteResult.cause());
                                lock.release();
                                log.debug("{}: Lock {} released", reconciliation, lockName);
                                handler.handle(deleteResult);
                            }
                        });
                    }

                } catch (Throwable ex) {
                    lock.release();
                    log.error("{}: Reconciliation failed", reconciliation, ex);
                    log.debug("{}: Lock {} released", reconciliation, lockName);
                    handler.handle(Future.failedFuture(ex));
                }
            } else {
                log.warn("{}: Failed to acquire lock {}.", reconciliation, lockName);
            }
        });
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
     * Reconcile zookeeper restore resources in the given namespace having the given selector.
     * Reconciliation works by getting the ZookeeperRestore custom resources in the given namespace with the given selector and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     *
     * @param trigger   A description of the triggering event (timer or watch), used for logging
     * @param namespace The namespace
     * @param selector  The labels used to select the resources
     */
    @Override
    public final void reconcileAll(String trigger, String namespace, Labels selector) {
        final List<ZookeeperRestore> desiredResources = crdOperator.list(namespace, selector);
        final Set<String> desiredNames = desiredResources.stream().map(cm -> cm.getMetadata().getName()).collect(Collectors.toSet());

        log.debug("reconcileAll({}, {}): desired resources with labels {}: {}", RESOURCE_KIND, trigger, selector, desiredNames);

        for (String name : desiredNames) {
            log.debug("reconcileAll({}, {}): ZookeeperBackup: {}", name);
            Reconciliation reconciliation = new Reconciliation(trigger, ResourceType.ZOOKEEPERRESTORE, namespace, name);
            reconcile(reconciliation, result -> {
                handleResult(reconciliation, result);
            });

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
        List<HasMetadata> result = new ArrayList<>();
        result.addAll(secretOperations.list(namespace, selector));
        result.addAll(jobOperator.list(namespace, selector));
        return result;
    }

    @Override
    public int getPort() {
        return HEALTH_SERVER_PORT;
    }

    /**
     * Create Kubernetes watch for ZookeeperRestore resources
     *
     * @param namespace Namespace where to watch for zookeeper restore
     * @param selector  Labels which the Users should match
     * @param onClose   Callbeck called when the watch is closed
     * @return Future
     */
    @Override
    public Future<Watch> createWatch(String namespace, Labels selector, Consumer<KubernetesClientException> onClose) {
        Future<Watch> result = Future.future();
        vertx.<Watch>executeBlocking(
            future -> {
                Watch watch = crdOperator.watch(namespace, selector, new Watcher<ZookeeperRestore>() {
                    @Override
                    public void eventReceived(Action action, ZookeeperRestore crd) {
                        String name = crd.getMetadata().getName();
                        switch (action) {
                            case ADDED:
                            case DELETED:
                            case MODIFIED:
                                Reconciliation reconciliation = new Reconciliation("watch", ResourceType.ZOOKEEPERRESTORE, namespace, name);
                                log.info("{}: {} {} in namespace {} was {}", reconciliation, RESOURCE_KIND, name, namespace, action);
                                reconcile(reconciliation, result -> {
                                    handleResult(reconciliation, result);
                                });
                                break;
                            case ERROR:
                                log.error("Failed {} {} in namespace{} ", RESOURCE_KIND, name, namespace);
                                reconcileAll("watch error", namespace, selector);
                                break;
                            default:
                                log.error("Unknown action: {} in namespace {}", name, namespace);
                                reconcileAll("watch unknown", namespace, selector);
                        }
                    }

                    @Override
                    public void onClose(KubernetesClientException e) {
                        onClose.accept(e);
                    }
                });
                future.complete(watch);
            }, result.completer()
        );
        return result;
    }

    /**
     * Log the reconciliation outcome.
     *
     * @param reconciliation Reconciliation
     * @param result         AsyncResult
     */
    private void handleResult(Reconciliation reconciliation, AsyncResult<Void> result) {
        if (result.succeeded()) {
            log.info("{}: ZookeeperRestore reconciled", reconciliation);
        } else {
            Throwable cause = result.cause();
            log.warn("{}: Failed to reconcile", reconciliation, cause);
        }
    }
}
