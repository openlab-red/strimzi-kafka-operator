/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.zookeeper.model.ZookeeperBackupModel;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Operator for a Zookeeper Backup.
 */
public class ZookeeperBackupOperator implements ZookeeperOperator<ZookeeperBackup> {

    private static final Logger log = LogManager.getLogger(ZookeeperBackupOperator.class.getName());
    private static final int LOCK_TIMEOUT_MS = 10;
    private static final String RESOURCE_KIND = "ZookeeperBackup";
    private final Vertx vertx;
    private final CrdOperator crdOperator;
    private final SecretOperator secretOperations;
    private final PvcOperator pvcOperations;
    private final CronJobOperator cronJobOperator;
    private final CertManager certManager;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;

    /**
     * @param vertx            The Vertx instance
     * @param certManager      For managing certificates
     * @param crdOperator      For operating on Custom Resources
     * @param secretOperations For operating on Secrets
     * @param pvcOperations    For operating on Persistent Volume Claim
     * @param cronJobOperator    For operating on Cron Job
     * @param caCertName       The name of the Secret containing the cluster CA certificate
     * @param caKeyName       The name of the Secret containing the cluster CA private key
     * @param caNamespace      The namespace of the Secret containing the cluster CA
     */
    public ZookeeperBackupOperator(Vertx vertx,
                                   CertManager certManager,
                                   CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdOperator,
                                   SecretOperator secretOperations,
                                   PvcOperator pvcOperations,
                                   CronJobOperator cronJobOperator,
                                   String caCertName, String caKeyName, String caNamespace) {
        this.vertx = vertx;
        this.certManager = certManager;
        this.secretOperations = secretOperations;
        this.pvcOperations = pvcOperations;
        this.cronJobOperator = cronJobOperator;
        this.crdOperator = crdOperator;
        this.caCertName = caCertName;
        this.caKeyName = caKeyName;
        this.caNamespace = caNamespace;
    }

    /**
     * Creates or updates the zookeeper backup. The implementation
     * should not assume that any resources are in any particular state (e.g. that the absence on
     * one resource means that all resources need to be created).
     *
     * @param reconciliation  Unique identification for the reconciliation
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     * @param clusterCaCert   Secret with the Cluster CA cert
     * @param clusterCaKey   Secret with the Cluster CA key
     * @param certSecret      Secret with the current certificate
     * @param handler         Completion handler
     */
    @Override
    public void createOrUpdate(Reconciliation reconciliation, ZookeeperBackup zookeeperBackup, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret, Handler<AsyncResult<Void>> handler) {
        final String namespace = reconciliation.namespace();
        final String name = reconciliation.name();
        final Labels labels = Labels.fromResource(zookeeperBackup).withKind(zookeeperBackup.getKind());

        ZookeeperBackupModel zookeeperBackupModel;

        try {
            zookeeperBackupModel = new ZookeeperBackupModel(namespace, name, labels);
            zookeeperBackupModel.fromCrd(certManager, zookeeperBackup, clusterCaCert, clusterCaKey, certSecret);
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
            return;
        }

        log.debug("{}: Updating ZookeeperBackup {} in namespace {}", reconciliation, name, namespace);

        Secret desired = zookeeperBackupModel.getSecret();
        PersistentVolumeClaim desiredPvc = zookeeperBackupModel.getStorage();
        CronJob desiredCronJob = zookeeperBackupModel.getCronJob();

        CompositeFuture.join(
            secretOperations.reconcile(namespace, zookeeperBackupModel.getName(), desired),
            pvcOperations.reconcile(namespace, zookeeperBackupModel.getName(), desiredPvc),
            cronJobOperator.reconcile(namespace, zookeeperBackupModel.getName(), desiredCronJob))
            .map((Void) null).setHandler(handler);

    }


    /**
     * Deletes the zookeeper backup
     *
     * @param reconciliation Reconciliation
     * @param handler Completion handler
     */
    @Override
    public void delete(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        log.debug("{}: Deleting ZookeeperBackup", reconciliation, name, namespace);

        CompositeFuture.join(
            secretOperations.reconcile(namespace, name, null),
            pvcOperations.reconcile(namespace, name, null),
            cronJobOperator.reconcile(namespace, name, null))
            .map((Void) null).setHandler(handler);

    }

    /**
     * Reconcile assembly resources in the given namespace having the given {@code name}.
     * Reconciliation works by getting the assembly resource (e.g. {@code ZookeeperBackup}) in the given namespace with the given name and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     */
    @Override
    public final void reconcile(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        final String lockName = getLockName(namespace, name, ResourceType.ZOOKEEPERBACKUP);

        vertx.sharedData().getLockWithTimeout(lockName, LOCK_TIMEOUT_MS, res -> {
            if (res.succeeded()) {
                log.debug("{}: Lock {} acquired", reconciliation, lockName);
                Lock lock = res.result();

                try {
                    ZookeeperBackup cr = (ZookeeperBackup) crdOperator.get(namespace, name);
                    if (cr != null) {
                        log.info("{}: CronJob {} should be created or updated", reconciliation, name);
                        Secret clusterCaCert = secretOperations.get(caNamespace, caCertName);
                        Secret clusterCaKey = secretOperations.get(caNamespace, caKeyName);
                        Secret backupSecret = secretOperations.get(namespace, name);

                        createOrUpdate(reconciliation, cr, clusterCaCert, clusterCaKey, backupSecret, createResult -> {
                            lock.release();
                            log.debug("{}: Lock {} released", reconciliation, lockName);
                            if (createResult.failed()) {
                                log.error("{}: createOrUpdate failed", reconciliation, createResult.cause());
                            } else {
                                handler.handle(createResult);
                            }
                        });
                    } else {
                        log.info("{}: ZookeeperBackup {} should be deleted", reconciliation, name);
                        delete(reconciliation, deleteResult -> {
                            if (deleteResult.succeeded()) {
                                log.info("{}: ZookeeperBackup {} deleted", reconciliation, name);
                                lock.release();
                                log.debug("{}: ZookeeperBackup {} released", reconciliation, lockName);
                                handler.handle(deleteResult);
                            } else {
                                log.error("{}: Deletion of ZookeeperBackup {} failed", reconciliation, name, deleteResult.cause());
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
     * Reconcile zookeeper backup resources in the given namespace having the given selector.
     * Reconciliation works by getting the ZookeeperBackup custom resources in the given namespace with the given selector and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     *
     * @param trigger   A description of the triggering event (timer or watch), used for logging
     * @param namespace The namespace
     * @param selector  The labels used to select the resources
     * @return CountDownLatch
     */
    @Override
    public final CountDownLatch reconcileAll(String trigger, String namespace, Labels selector) {

        List<ZookeeperBackup> desiredResources = crdOperator.list(namespace, selector);
        Set<String> desiredNames = desiredResources.stream().map(cm -> cm.getMetadata().getName()).collect(Collectors.toSet());
        log.debug("reconcileAll({}, {}): desired resources with labels {}: {}", RESOURCE_KIND, trigger, selector, desiredNames);

        Labels resourceSelector = selector.withKind(RESOURCE_KIND);
        List<? extends HasMetadata> resources = getResources(namespace, resourceSelector);
        Set<String> resourceNames = resources.stream()
            .filter(r -> !r.getKind().equals(RESOURCE_KIND)) // exclude desired resource
            .map(r -> ((HasMetadata) r).getMetadata().getName())
            .collect(Collectors.toSet());

        log.debug("reconcileAll({}, {}): Other resources with labels {}: {}", RESOURCE_KIND, trigger, resourceSelector, resourceNames);

        CountDownLatch outerLatch = new CountDownLatch(1);

        vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(
            future -> {
                try {
                    //TODO:
                    List<String> emptyList = Collections.emptyList();
                    future.complete(emptyList);
                } catch (Throwable t) {
                    future.failed();
                }
            }, res -> {
                if (res.succeeded()) {
                    log.debug("reconcileAll({}, {}): CronJobs with ZookeeperBackup: {}", RESOURCE_KIND, trigger, res.result());
                    desiredNames.addAll((Collection<? extends String>) res.result());
                    desiredNames.addAll(resourceNames);

                    AtomicInteger counter = new AtomicInteger(desiredNames.size());
                    for (String name : desiredNames) {
                        Reconciliation reconciliation = new Reconciliation(trigger, ResourceType.ZOOKEEPERBACKUP, namespace, name);
                        reconcile(reconciliation, result -> {
                            handleResult(reconciliation, result);
                            if (counter.getAndDecrement() == 0) {
                                outerLatch.countDown();
                            }
                        });
                    }
                } else {
                    log.error("Error while getting ZookeeperBackup spec");
                }
                return;
            });


        return outerLatch;
    }

    /**
     * Gets all resources relevant to ZookeeperBackup
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return List
     */
    @Override
    public List<HasMetadata> getResources(String namespace, Labels selector) {
        List<HasMetadata> result = new ArrayList<>();
        result.addAll(secretOperations.list(namespace, selector));
        result.addAll(pvcOperations.list(namespace, selector));
        result.addAll(cronJobOperator.list(namespace, selector));
        return result;
    }

    /**
     * Create Kubernetes watch for ZookeeperBackup resources
     *
     * @param namespace Namespace where to watch for zookeeper backup
     * @param selector  Labels which the Users should match
     * @param onClose   Callbeck called when the watch is closed
     * @return Future
     */
    @Override
    public Future<Watch> createWatch(String namespace, Labels selector, Consumer<KubernetesClientException> onClose) {
        Future<Watch> result = Future.future();
        vertx.<Watch>executeBlocking(
            future -> {
                Watch watch = crdOperator.watch(namespace, selector, new Watcher<ZookeeperBackup>() {
                    @Override
                    public void eventReceived(Action action, ZookeeperBackup crd) {
                        String name = crd.getMetadata().getName();
                        switch (action) {
                            case ADDED:
                            case DELETED:
                            case MODIFIED:
                                Reconciliation reconciliation = new Reconciliation("watch", ResourceType.ZOOKEEPERBACKUP, namespace, name);
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
     * @param reconciliation Reconciliation
     * @param result AsyncResult
     */
    private void handleResult(Reconciliation reconciliation, AsyncResult<Void> result) {
        if (result.succeeded()) {
            log.info("{}: ZookeeperBackup reconciled", reconciliation);
        } else {
            Throwable cause = result.cause();
            log.warn("{}: Failed to reconcile", reconciliation, cause);
        }
    }
}
