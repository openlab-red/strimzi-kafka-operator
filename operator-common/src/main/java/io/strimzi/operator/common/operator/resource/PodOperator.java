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
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
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
    private static final long TIMEOUT_MS = 60000L;

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
     * Wait Container Status of Pod
     *
     * @param namespace
     * @param selector
     * @param containerName
     * @return
     */
    public Future<Pod> waitContainerIsTerminated(String namespace, Labels selector, String containerName) {
        final List<Pod> pods = list(namespace, selector).stream().sorted(
            Comparator.comparing(p -> p.getMetadata().getName())
        ).collect(Collectors.toList());

        if (pods.size() > 0) {
            final Pod pod = pods.get(0);
            final String name = pod.getMetadata().getName();
            return waitFor(namespace, name, POLL_INTERVAL_MS, TIMEOUT_MS,
                (a, b) -> isTerminated(getContainerStatus(pod, containerName), 0))
                .compose(res -> Future.succeededFuture(pod));
        }

        return Future.succeededFuture();

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
