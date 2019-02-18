/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator.backup;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
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
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Operator for a Zookeeper Backup.
 */
public class ZookeeperBackupOperator {
    private static final Logger log = LogManager.getLogger(ZookeeperBackupOperator.class.getName());
    private static final int LOCK_TIMEOUT_MS = 10;
    private static final String RESOURCE_KIND = "ZookeeperBackup";
    private final Vertx vertx;
    private final CrdOperator crdOperator;
    private final SecretOperator secretOperations;
    private final CertManager certManager;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;

    /**
     * @param vertx            The Vertx instance
     * @param certManager      For managing certificates
     * @param crdOperator      For operating on Custom Resources
     * @param secretOperations For operating on Secrets
     * @param caCertName       The name of the Secret containing the clients CA certificate and private key
     * @param caNamespace      The namespace of the Secret containing the clients CA certificate and private key
     */
    public ZookeeperBackupOperator(Vertx vertx,
                                   CertManager certManager,
                                   CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdOperator,
                                   SecretOperator secretOperations,
                                   String caCertName, String caKeyName, String caNamespace) {
        this.vertx = vertx;
        this.certManager = certManager;
        this.secretOperations = secretOperations;
        this.crdOperator = crdOperator;
        this.caCertName = caCertName;
        this.caKeyName = caKeyName;
        this.caNamespace = caNamespace;
    }

    /**
     * Gets the name of the lock to be used for operating on the given {@code namespace} and
     * cluster {@code name}
     *
     * @param namespace The namespace containing the cluster
     * @param name      The name of the cluster
     */
    private final String getLockName(String namespace, String name) {
        return "lock::" + namespace + "::" + RESOURCE_KIND + "::" + name;
    }

    /**
     * Creates or updates the zookeeper backup. The implementation
     * should not assume that any resources are in any particular state (e.g. that the absence on
     * one resource means that all resources need to be created).
     *
     * @param reconciliation  Unique identification for the reconciliation
     * @param zookeeperBackup ZookeeperBackup resources with the desired zookeeper backup configuration.
     * @param clientsCaCert   Secret with the Clients CA cert
     * @param clientsCaCert   Secret with the Clients CA key
     * @param handler         Completion handler
     */
    protected void createOrUpdate(Reconciliation reconciliation, ZookeeperBackup zookeeperBackup, Secret clientsCaCert, Secret clientsCaKey, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();

    }

    /**
     * Deletes the zookeeper backup
     *
     * @param handler Completion handler
     */
    protected void delete(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();

    }

    /**
     * Reconcile assembly resources in the given namespace having the given {@code name}.
     * Reconciliation works by getting the assembly resource (e.g. {@code ZookeeperBackup}) in the given namespace with the given name and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     */
    public final void reconcile(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        final String lockName = getLockName(namespace, name);
        vertx.sharedData().getLockWithTimeout(lockName, LOCK_TIMEOUT_MS, res -> {
            if (res.succeeded()) {
                log.debug("{}: Lock {} acquired", reconciliation, lockName);
                Lock lock = res.result();

                try {

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
    public final CountDownLatch reconcileAll(String trigger, String namespace, Labels selector) {

        CountDownLatch outerLatch = new CountDownLatch(1);


        return outerLatch;
    }

    /**
     * Gets all resources relevant to ZookeeperBackup
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return
     */
    private List<HasMetadata> getResources(String namespace, Labels selector) {
        List<HasMetadata> result = new ArrayList<>();
        result.addAll(secretOperations.list(namespace, selector));
        return result;
    }

    /**
     * Create Kubernetes watch for ZookeeperBackup resources
     *
     * @param namespace Namespace where to watch for zookeeper backup
     * @param selector  Labels which the Users should match
     * @param onClose   Callbeck called when the watch is closed
     * @return
     */
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
