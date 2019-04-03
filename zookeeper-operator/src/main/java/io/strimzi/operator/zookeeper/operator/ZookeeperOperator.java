/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.certs.CertManager;
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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    protected abstract void containerAddModWatch(Watcher.Action action, Pod pod, String name, String namespace);
}
