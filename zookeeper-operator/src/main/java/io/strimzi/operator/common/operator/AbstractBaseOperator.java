/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.exception.InvalidConfigParameterException;
import io.strimzi.operator.common.exception.InvalidResourceException;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.NamespaceAndName;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.model.ResourceVisitor;
import io.strimzi.operator.common.model.ValidationVisitor;
import io.strimzi.operator.common.operator.resource.AbstractResourceOperator;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
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
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>Abstract assembly creation, update, read, deletion, etc.</p>
 *
 * <p>An assembly is a collection of Kubernetes resources of various types
 * (e.g. Services, StatefulSets, Deployments etc) which operate together to provide some functionality.</p>
 *
 * <p>This class manages a per-assembly locking strategy so only one operation per assembly
 * can proceed at once.</p>
 */
public abstract class AbstractBaseOperator<C extends KubernetesClient, T extends HasMetadata,
    L extends KubernetesResourceList/*<T>*/, D extends Doneable<T>, R extends Resource<T, D>> implements Operator<T> {

    private static final Logger log = LogManager.getLogger(AbstractBaseOperator.class.getName());

    protected final Vertx vertx;
    protected final ResourceType assemblyType;
    protected final CertManager certManager;
    protected final AbstractWatchableResourceOperator<C, T, L, D, R> resourceOperator;
    protected final String kind;
    protected final ImagePullPolicy imagePullPolicy;

    protected static final int LOCK_TIMEOUT_MS = 10000;

    /**
     * @param vertx            The Vertx instance
     * @param assemblyType     Assembly type
     * @param certManager      Certificate manager
     * @param resourceOperator For operating on the desired resource
     * @param imagePullPolicy  Image Pull Policy
     */
    public AbstractBaseOperator(Vertx vertx, ResourceType assemblyType, CertManager certManager, AbstractWatchableResourceOperator<C, T, L, D, R> resourceOperator, ImagePullPolicy imagePullPolicy) {
        this.vertx = vertx;
        this.assemblyType = assemblyType;
        this.certManager = certManager;
        this.resourceOperator = resourceOperator;
        this.kind = assemblyType.name;
        this.imagePullPolicy = imagePullPolicy;
    }

    /**
     * Gets the name of the lock to be used for operating on the given {@code assemblyType}, {@code namespace} and
     * cluster {@code name}
     *
     * @param assemblyType The type of cluster
     * @param namespace    The namespace containing the cluster
     * @param name         The name of the cluster
     * @return String
     */
    public final String getLockName(ResourceType assemblyType, String namespace, String name) {
        return "lock::" + namespace + "::" + assemblyType + "::" + name;
    }

    /**
     * Subclasses implement this method to create or update the cluster. The implementation
     * should not assume that any resources are in any particular state (e.g. that the absence on
     * one resource means that all resources need to be created).
     *
     * @param reconciliation   Unique identification for the reconciliation
     * @param assemblyResource Resources with the desired cluster configuration.
     * @return Future
     */
    protected abstract Future<Void> createOrUpdate(Reconciliation reconciliation, T assemblyResource);

    /**
     * The name of the given {@code resource}, as read from its metadata.
     *
     * @param resource The resource
     * @return String
     */
    protected static String name(HasMetadata resource) {
        if (resource != null) {
            ObjectMeta metadata = resource.getMetadata();
            if (metadata != null) {
                return metadata.getName();
            }
        }
        return null;
    }

    /**
     * Delete Resource with selector Labels.forKind().withName()
     *
     * @param operator  AbstractResourceOperator
     * @param namespace Namespace where to search for resources
     * @param name      Name which the resources should have
     * @return Future
     */
    protected Future<CompositeFuture> deleteResourceWithName(AbstractResourceOperator operator, String namespace, String name) {
        List<? extends HasMetadata> resources = operator.list(namespace, Labels.forKind(this.kind).withName(name));
        List<Future> result = new ArrayList<>();

        resources.stream().forEach(s -> {
            operator.reconcile(namespace, s.getMetadata().getName(), null);
        });
        return CompositeFuture.join(result);
    }

    /**
     * Reconcile assembly resources in the given namespace having the given {@code name}.
     * Reconciliation works by getting the assembly resource (e.g. {@code KafkaAssembly}) in the given namespace with the given name and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     * <ul>
     * <li>An assembly will be {@linkplain #createOrUpdate(Reconciliation, HasMetadata) created or updated} if ConfigMap is without same-named resources</li>
     * </ul>
     *
     * @param reconciliation Unique identification for the reconciliation
     * @param handler        handler
     */
    @Override
    public final void reconcile(Reconciliation reconciliation, Handler<AsyncResult<Void>> handler) {
        String namespace = reconciliation.namespace();
        String assemblyName = reconciliation.name();
        final String lockName = getLockName(assemblyType, namespace, assemblyName);
        vertx.sharedData().getLockWithTimeout(lockName, LOCK_TIMEOUT_MS, res -> {
            if (res.succeeded()) {
                log.debug("{}: Lock {} acquired", reconciliation, lockName);
                Lock lock = res.result();

                try {
                    // get CustomResource and related resources for the specific cluster
                    T cr = resourceOperator.get(namespace, assemblyName);
                    validate(cr);

                    if (cr != null) {
                        log.info("{}: Assembly {} should be created or updated", reconciliation, assemblyName);
                        createOrUpdate(reconciliation, cr)
                            .setHandler(createResult -> {
                                lock.release();
                                log.debug("{}: Lock {} released", reconciliation, lockName);
                                if (createResult.failed()) {
                                    if (createResult.cause() instanceof InvalidResourceException) {
                                        log.error("{}: createOrUpdate failed. {}", reconciliation, createResult.cause().getMessage());
                                    } else {
                                        log.error("{}: createOrUpdate failed", reconciliation, createResult.cause());
                                    }
                                } else {
                                    handler.handle(createResult);
                                }
                            });
                    } else {
                        log.info("{}: Assembly {} should be deleted by garbage collection", reconciliation, assemblyName);
                        lock.release();
                        log.debug("{}: Lock {} released", reconciliation, lockName);
                        handler.handle(Future.succeededFuture());
                    }
                } catch (Throwable ex) {
                    lock.release();
                    log.debug("{}: Lock {} released", reconciliation, lockName);
                    handler.handle(Future.failedFuture(ex));
                }
            } else {
                log.debug("{}: Failed to acquire lock {}.", reconciliation, lockName);
            }
        });
    }

    /**
     * Validate the Custom Resource.
     * This should log at the WARN level (rather than throwing) if the resource can safely be reconciled.
     *
     * @param resource The custom resource
     * @throws InvalidResourceException if the resource cannot be safely reconciled.
     */
    protected void validate(T resource) {
        if (resource != null) {
            ResourceVisitor.visit(resource, new ValidationVisitor(resource, log));
        }
    }

    /**
     * Reconcile assembly resources in the given namespace having the given selector.
     * Reconciliation works by getting the assembly ConfigMaps in the given namespace with the given selector and
     * comparing with the corresponding {@linkplain #getResources(String, Labels) resource}.
     * <ul>
     * <li>An assembly will be {@linkplain #createOrUpdate(Reconciliation, HasMetadata) created} for all ConfigMaps without same-named resources</li>
     * </ul>
     *
     * @param trigger   A description of the triggering event (timer or watch), used for logging
     * @param namespace The namespace
     * @return CountDownLatch
     */
    @Override
    public final CountDownLatch reconcileAll(String trigger, String namespace) {

        List<T> desiredResources = resourceOperator.list(namespace, Labels.EMPTY);
        Set<NamespaceAndName> desiredNames = desiredResources.stream()
            .map(cr -> new NamespaceAndName(cr.getMetadata().getNamespace(), cr.getMetadata().getName()))
            .collect(Collectors.toSet());
        log.debug("reconcileAll({}, {}): desired resources with labels {}: {}", assemblyType, trigger, Labels.EMPTY, desiredNames);

        // get resources with kind=cluster&type=kafka (or connect, or connect-s2i)
        Labels resourceSelector = Labels.EMPTY.withKind(assemblyType.name);
        List<? extends HasMetadata> resources = getResources(namespace, resourceSelector);
        // now extract the cluster name from those
        Set<NamespaceAndName> resourceNames = resources.stream()
            .filter(r -> !r.getKind().equals(kind)) // exclude desired resource
            .map(resource ->
                new NamespaceAndName(
                    resource.getMetadata().getNamespace(),
                    resource.getMetadata().getLabels().get(Labels.STRIMZI_CLUSTER_LABEL)
                )
            )
            .collect(Collectors.toSet());
        log.debug("reconcileAll({}, {}): Other resources with labels {}: {}", assemblyType, trigger, resourceSelector, resourceNames);

        desiredNames.addAll(resourceNames);

        // We use a latch so that callers (specifically, test callers) know when the reconciliation is complete
        // Using futures would be more complex for no benefit
        CountDownLatch latch = new CountDownLatch(desiredNames.size());

        for (NamespaceAndName name : desiredNames) {
            Reconciliation reconciliation = new Reconciliation(trigger, assemblyType, name.getNamespace(), name.getName());
            reconcile(reconciliation, result -> {
                handleResult(reconciliation, result);
                latch.countDown();
            });
        }

        return latch;
    }

    /**
     * Gets all the assembly resources (for all assemblies) in the given namespace.
     * Assembly resources (e.g. the {@code KafkaAssembly} resource) may be included in the result.
     *
     * @param namespace The namespace
     * @param selector  labels selector
     * @return The matching resources.
     */
    protected abstract List<HasMetadata> getResources(String namespace, Labels selector);

    public Future<Watch> createWatch(String watchNamespace, Consumer<KubernetesClientException> onClose) {
        Future<Watch> result = Future.future();
        vertx.<Watch>executeBlocking(
            future -> {
                Watch watch = resourceOperator.watch(watchNamespace, new Watcher<T>() {
                    @Override
                    public void eventReceived(Action action, T cr) {
                        String name = cr.getMetadata().getName();
                        String resourceNamespace = cr.getMetadata().getNamespace();
                        switch (action) {
                            case ADDED:
                            case DELETED:
                            case MODIFIED:
                                Reconciliation reconciliation = new Reconciliation("watch", assemblyType, resourceNamespace, name);
                                log.info("{}: {} {} in namespace {} was {}", reconciliation, kind, name, resourceNamespace, action);
                                reconcile(reconciliation, result -> {
                                    handleResult(reconciliation, result);
                                });
                                break;
                            case ERROR:
                                log.error("Failed {} {} in namespace{} ", kind, name, resourceNamespace);
                                reconcileAll("watch error", watchNamespace);
                                break;
                            default:
                                log.error("Unknown action: {} in namespace {}", name, resourceNamespace);
                                reconcileAll("watch unknown", watchNamespace);
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
     * @param reconciliation Unique identification for the reconciliation
     * @param result         handler
     */
    private void handleResult(Reconciliation reconciliation, AsyncResult<Void> result) {
        if (result.succeeded()) {
            log.info("{}: Assembly reconciled", reconciliation);
        } else {
            Throwable cause = result.cause();
            if (cause instanceof InvalidConfigParameterException) {
                log.warn("{}: Failed to reconcile {}", reconciliation, cause.getMessage());
            } else {
                log.warn("{}: Failed to reconcile", reconciliation, cause);
            }
        }
    }

}
