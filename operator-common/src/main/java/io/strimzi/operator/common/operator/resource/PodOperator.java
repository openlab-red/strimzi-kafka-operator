/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.strimzi.operator.common.Util;
import io.strimzi.operator.common.model.Labels;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Operations for {@code Pod}s, which support {@link #isReady(String, String)} and
 * {@link #watch(String, String, Watcher)} in addition to the usual operations.
 */
public class PodOperator extends AbstractReadyResourceOperator<KubernetesClient, Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> {

    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long TIMEOUT_MS = 120000L;

    /**
     * Constructor
     *
     * @param vertx  The Vertx instance
     * @param client The Kubernetes client
     */
    public PodOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, "Pods");
    }

    @Override
    protected MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> operation() {
        return client.pods();
    }

    /**
     * Watch the pod identified by the given {@code namespace} and {@code name} using the given {@code watcher}.
     *
     * @param namespace The namespace
     * @param name      The name
     * @param watcher   The watcher
     * @return The watch
     */
    public Watch watch(String namespace, String name, Watcher<Pod> watcher) {
        return operation().inNamespace(namespace).withName(name).watch(watcher);
    }

    /**
     * Wait Container is Terminated
     *
     * @param namespace     namespace
     * @param selector      Pod selector
     * @param containerName container name
     * @return Future
     * TODO: make it generic
     */
    public Future<Pod> waitContainerIsTerminated(String namespace, Labels selector, String containerName) {
        Future<Pod> future = Future.future();
        return Util.waitFor(vertx, "All pods matching " + selector + " to be ready",
            POLL_INTERVAL_MS, TIMEOUT_MS,
            () -> isTerminated(namespace, selector, containerName, future))
            .compose(res -> future);
    }

    //TODO: make it generic
    private boolean isTerminated(String namespace, Labels selector, String containerName, Future<Pod> future) {

        List<Pod> pods = list(namespace, selector).stream()
            .filter(p -> p.getStatus().getPhase().equals("Running"))
            .filter(p -> !Readiness.isPodReady(p))
            .sorted(Comparator.comparing(p -> p.getMetadata().getName()))
            .collect(Collectors.toList());

        if (pods.size() > 0) {
            final Pod pod = pods.get(0);
            final String name = pod.getMetadata().getName();

            if (isTerminated(getContainerStatus(pod, containerName), 0)) {
                log.debug(" Container in pod {} is Terminated : {}", name, containerName);
                future.complete(pod);
                return true;
            }
            log.debug(" Container in pod {} not Terminated : {}", name, containerName);
        }

        return false;
    }


    /**
     * Terminate Container
     *
     * @param namespace     namespace
     * @param name          Pod name where the container is running
     * @param containerName name of the container
     * @return ExecWatch
     */
    public Future<ExecWatch> terminateContainer(String namespace, String name, String containerName) {

        final ExecWatch exit = client.pods().
            inNamespace(namespace)
            .withName(name)
            .inContainer(containerName)
            .redirectingOutput()
            .redirectingError()
            .exec("kill", "1");
        log.debug(" Container {} in pod {} exec output : {}", name, containerName, exit.getOutput());
        return Future.succeededFuture(exit);
    }


    /**
     * Get container log
     *
     * @param namespace     namespace
     * @param name          Pod name where the container is running
     * @param containerName name of the container
     * @return String
     */
    public Future<String> getContainerLog(String namespace, String name, String containerName) {

        final String containerLog = client.pods()
            .inNamespace(namespace)
            .withName(name)
            .inContainer(containerName)
            .getLog();
        log.debug(" Container {} in pod {} log : {}", name, containerName, containerLog);
        return Future.succeededFuture(containerLog);
    }

    /**
     * Retrieve the status of container inside a pod
     *
     * @param containerName name of the container
     * @param pod           pod where the container is running
     * @return ContainerStatus
     */
    public ContainerStatus getContainerStatus(Pod pod, String containerName) {
        final List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();

        return containerStatuses.stream()
            .filter(container -> containerName.equals(container.getName()))
            .findAny()
            .orElse(null);
    }

    /**
     * Check if the status of a container is terminated
     *
     * @param containerStatus status of the cotnainer
     * @param exitCode        which exit code
     * @return boolean
     */
    public static boolean isTerminated(ContainerStatus containerStatus, Integer exitCode) {
        return containerStatus != null && containerStatus.getState() != null
            && containerStatus.getState().getTerminated() != null
            && exitCode.equals(containerStatus.getState().getTerminated().getExitCode());
    }
}
