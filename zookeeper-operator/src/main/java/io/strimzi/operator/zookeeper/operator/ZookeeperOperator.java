/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.AbstractBaseOperator;
import io.strimzi.operator.common.operator.resource.AbstractWatchableResourceOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.EventOperator;
import io.strimzi.operator.common.operator.resource.JobOperator;
import io.strimzi.operator.common.operator.resource.NetworkPolicyOperator;
import io.strimzi.operator.common.operator.resource.PodOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.ResourceOperatorFacade;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.zookeeper.model.ZookeeperOperatorResources;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Operator for a Zookeeper Operator.
 */
public abstract class ZookeeperOperator<C extends KubernetesClient, T extends HasMetadata,
    L extends KubernetesResourceList/*<T>*/, D extends Doneable<T>, R extends Resource<T, D>> extends AbstractBaseOperator<C, T, L, D, R> {

    private static final Logger log = LogManager.getLogger(ZookeeperOperator.class.getName());
    protected final SecretOperator secretOperator;
    protected final PvcOperator pvcOperator;
    protected final CronJobOperator cronJobOperator;
    protected final PodOperator podOperator;
    protected final EventOperator eventOperator;
    protected final JobOperator jobOperator;
    protected final String caCertName;
    protected final String caKeyName;
    protected final String caNamespace;
    protected final NetworkPolicyOperator networkPolicyOperator;


    /**
     * @param vertx                  The Vertx instance
     * @param assemblyType           The resource type
     * @param certManager            For managing certificates
     * @param resourceOperator       For operating on Custom Resources
     * @param resourceOperatorFacade For operating on Kubernetes Resource
     * @param caCertName             The name of the Secret containing the cluster CA certificate
     * @param caKeyName              The name of the Secret containing the cluster CA private key
     * @param caNamespace            The namespace of the Secret containing the cluster CA
     * @param imagePullPolicy        Image pull policy configured by the user
     */
    public ZookeeperOperator(Vertx vertx,
                             ResourceType assemblyType,
                             CertManager certManager,
                             AbstractWatchableResourceOperator<C, T, L, D, R> resourceOperator,
                             ResourceOperatorFacade resourceOperatorFacade,
                             String caCertName, String caKeyName, String caNamespace,
                             ImagePullPolicy imagePullPolicy) {

        super(vertx, assemblyType, certManager, resourceOperator, imagePullPolicy);
        this.secretOperator = resourceOperatorFacade.getSecretOperator();
        this.pvcOperator = resourceOperatorFacade.getPvcOperator();
        this.cronJobOperator = resourceOperatorFacade.getCronJobOperator();
        this.podOperator = resourceOperatorFacade.getPodOperator();
        this.eventOperator = resourceOperatorFacade.getEventOperator();
        this.jobOperator = resourceOperatorFacade.getJobOperator();
        this.networkPolicyOperator = resourceOperatorFacade.getNetworkPolicyOperator();
        this.caCertName = caCertName;
        this.caKeyName = caKeyName;
        this.caNamespace = caNamespace;

    }

    /**
     * Creates or updates the resource. The implementation
     * should not assume that any resources are in any particular state (e.g. that the absence on
     * one resource means that all resources need to be created).
     *
     * @param reconciliation Unique identification for the reconciliation
     * @param resource       Resource with the desired configuration.
     */
    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, T resource) {
        final String namespace = reconciliation.namespace();
        final String name = reconciliation.name();
        final Labels labels = Labels.fromResource(resource).withKind(resource.getKind());
        final String clusterName = labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);

        final Secret clusterCaCert = secretOperator.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperator.get(caNamespace, caKeyName);
        final Secret certSecret = secretOperator.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        log.debug("{}: Updating {} in namespace {}", reconciliation, name, namespace);
        return workflow(reconciliation, resource, namespace, name, clusterName, labels, clusterCaCert, clusterCaKey, certSecret);

    }


    /**
     * Run the reconcilation workflow
     *
     * @param reconciliation Unique identification for the reconciliation
     * @param resource       ZookeeperBackup resources with the desired zookeeper backup configuration.
     * @param namespace      Namespace where to search for resources
     * @param name           resource name
     * @param clusterName    cluster name
     * @param labels         resource labels
     * @param clusterCaCert  Secret containing the cluster CA certificate
     * @param clusterCaKey   Secret containing the cluster CA private key
     * @param certSecret     Secret containing the cluster CA
     * @return Future
     */
    protected abstract Future<Void> workflow(Reconciliation reconciliation, T resource, String namespace, String name, String clusterName, Labels labels, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret);


    @Override
    public Future<Watch> createWatch(String watchNamespace, Consumer<KubernetesClientException> onClose) {
        createWatchContainer(watchNamespace, onClose);
        return super.createWatch(watchNamespace, onClose);
    }


    protected Future<Watch> createWatchContainer(String namespace, Consumer<KubernetesClientException> onClose) {
        Future<Watch> result = Future.future();
        vertx.<Watch>executeBlocking(
            future -> {
                Watch watch = podOperator.watch(namespace, Labels.STRIMZI_KIND_LABEL, assemblyType.name, new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String name = pod.getMetadata().getName();
                        switch (action) {
                            case ADDED:
                            case MODIFIED:
                                containerAddModWatch(action, pod, name, namespace);
                                break;
                            case DELETED:
                                log.info("Deleted {} {} in namespace{} ", kind, name, namespace);
                                break;
                            case ERROR:
                                log.error("Failed {} {} in namespace{} ", kind, name, namespace);
                                break;
                            default:
                                log.error("Unknown action {} : {} in namespace {}", action, name, namespace);
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
     * Check if the job is Running to avoid concurrent execution.
     * This can happen if the CRD has been update for deletion but is still available
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return boolean
     */
    protected boolean isRunning(String namespace, Labels selector) {
        final List<Pod> list = podOperator.list(namespace, selector);
        if (list.size() > 0) { // it is always one.
            final Pod pod = list.get(0);
            final String phase = pod.getStatus().getPhase();
            log.info("Pod {} active with labels {} on {} status: {}", pod.getMetadata().getName(), selector, namespace, phase);
            return !phase.equals("Succeeded");
        }
        log.info("No pod running with labels {} on {}", selector, namespace);
        return false;
    }

    protected abstract void containerAddModWatch(Watcher.Action action, Pod pod, String name, String namespace);

    /**
     * Gets all resources relevant
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return List
     */
    @Override
    protected List<HasMetadata> getResources(String namespace, Labels selector) {
        return Collections.EMPTY_LIST;
    }

}
