/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
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
import io.strimzi.operator.common.Util;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.PodOperator;
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
import java.util.List;
import java.util.Set;
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
    private final PvcOperator pvcOperator;
    private final CronJobOperator cronJobOperator;
    private final PodOperator podOperator;
    private final CertManager certManager;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
    private static final int HEALTH_SERVER_PORT = 8081;


    /**
     * @param vertx            The Vertx instance
     * @param certManager      For managing certificates
     * @param crdOperator      For operating on Custom Resources
     * @param secretOperations For operating on Secrets
     * @param pvcOperator      For operating on Persistent Volume Claim
     * @param cronJobOperator  For operating on Cron Job
     * @param podOperator      For operating on Pod
     * @param caCertName       The name of the Secret containing the cluster CA certificate
     * @param caKeyName        The name of the Secret containing the cluster CA private key
     * @param caNamespace      The namespace of the Secret containing the cluster CA
     */
    public ZookeeperBackupOperator(Vertx vertx,
                                   CertManager certManager,
                                   CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdOperator,
                                   SecretOperator secretOperations,
                                   PvcOperator pvcOperator,
                                   CronJobOperator cronJobOperator,
                                   PodOperator podOperator,
                                   String caCertName, String caKeyName, String caNamespace) {
        this.vertx = vertx;
        this.certManager = certManager;
        this.secretOperations = secretOperations;
        this.pvcOperator = pvcOperator;
        this.cronJobOperator = cronJobOperator;
        this.crdOperator = crdOperator;
        this.podOperator = podOperator;
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
     * @param clusterCaKey    Secret with the Cluster CA key
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
            secretOperations.reconcile(namespace, desired.getMetadata().getName(), desired),
            pvcOperator.reconcile(namespace, desiredPvc.getMetadata().getName(), desiredPvc))
            .compose(res -> cronJobOperator.reconcile(namespace, desiredCronJob.getMetadata().getName(), desiredCronJob))
            .map((Void) null).setHandler(handler);

    }

    /**
     * Deletes the zookeeper backup
     *
     * @param reconciliation Reconciliation
     * @param handler        Completion handler
     */
    @Override
    public void delete(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        log.debug("{}: Deleting ZookeeperBackup", reconciliation, name, namespace);

        CompositeFuture.join(
            secretOperations.reconcile(namespace, name, null),
//            pvcOperator.reconcile(namespace, name, null), keep the storage TODO: Add condition based on deleteClaim.
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
     */
    @Override
    public final void reconcileAll(String trigger, String namespace, Labels selector) {
        final List<ZookeeperBackup> desiredResources = crdOperator.list(namespace, selector);
        final Labels resourceSelector = selector.withKind(RESOURCE_KIND);
        final Set<String> desiredNames = desiredResources.stream().map(cm -> cm.getMetadata().getName()).collect(Collectors.toSet());

        log.debug("reconcileAll({}, {}): desired resources with labels {}: {}", RESOURCE_KIND, trigger, selector, desiredNames);

        watchjobs(namespace, resourceSelector);

        for (String name : desiredNames) {
            log.debug("reconcileAll({}, {}): ZookeeperBackup: {}", name);
            Reconciliation reconciliation = new Reconciliation(trigger, ResourceType.ZOOKEEPERBACKUP, namespace, name);
            reconcile(reconciliation, result -> {
                handleResult(reconciliation, result);
            });
        }
    }

    //TODO sync with cron schedule.
    public void watchjobs(String namespace, Labels selector) {
        Util.waitFor(vertx, RESOURCE_KIND, 5000L, 60000L, () -> {
            log.info("pods list selector: {}", selector);
            List<Pod> pods = podOperator.list(namespace, selector);
            return pods.size() > 0;
        }).setHandler(r -> {
            if (r.succeeded()) {
                final List<Pod> pods = podOperator.list(namespace, selector).stream().sorted(
                    (p1, p2) -> p1.getMetadata().getName().compareTo(p2.getMetadata().getName())
                ).collect(Collectors.toList());

                final Pod pod = pods.get(0);
                final String name = pod.getMetadata().getName();
                log.info("pods list: {}", name);

                podOperator.waitFor(namespace, name, 1000L, 60000L, (a, b) -> {
                    final List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();

                    ContainerStatus containerStatus = containerStatuses.stream()
                        .filter(container -> "burry".equals(container.getName()))
                        .findAny()
                        .orElse(null);

                    return containerStatus != null && containerStatus.getState() != null && containerStatus.getState().getTerminated() != null && containerStatus.getState().getTerminated().getExitCode() == 0;
                }).setHandler(ready -> {
                    if (ready.succeeded()) {
                        log.info("Pod needs to be terminated: {}", name);
                        podOperator.reconcile(namespace, name, null);
                    }
                });

            }
        });
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
        result.addAll(pvcOperator.list(namespace, selector));
        result.addAll(cronJobOperator.list(namespace, selector));
        result.addAll(podOperator.list(namespace, selector));
        return result;
    }

    @Override
    public int getPort() {
        return HEALTH_SERVER_PORT;
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
     *
     * @param reconciliation Reconciliation
     * @param result         AsyncResult
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
