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
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.EventType;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.AbstractBaseOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.EventOperator;
import io.strimzi.operator.common.operator.resource.PodOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.utils.EventUtils;
import io.strimzi.operator.zookeeper.model.ZookeeperBackupModel;
import io.strimzi.operator.zookeeper.model.ZookeeperOperatorResources;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Operator for a Zookeeper Backup.
 */
public class ZookeeperBackupOperator extends AbstractBaseOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup, Resource<ZookeeperBackup, DoneableZookeeperBackup>> {

    private static final Logger log = LogManager.getLogger(ZookeeperBackupOperator.class.getName());
    private final SecretOperator secretOperations;
    private final PvcOperator pvcOperator;
    private final CronJobOperator cronJobOperator;
    private final PodOperator podOperator;
    private final EventOperator eventOperator;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
    private static final int HEALTH_SERVER_PORT = 8081;


    /**
     * @param vertx            The Vertx instance
     * @param assemblyType     The resource type
     * @param certManager      For managing certificates
     * @param resourceOperator For operating on Custom Resources
     * @param secretOperations For operating on Secrets
     * @param pvcOperator      For operating on Persistent Volume Claim
     * @param cronJobOperator  For operating on Cron Job
     * @param podOperator      For operating on Pod
     * @param eventOperator    For operating on Event
     * @param caCertName       The name of the Secret containing the cluster CA certificate
     * @param caKeyName        The name of the Secret containing the cluster CA private key
     * @param caNamespace      The namespace of the Secret containing the cluster CA
     */
    public ZookeeperBackupOperator(Vertx vertx,
                                   ResourceType assemblyType,
                                   CertManager certManager,
                                   CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> resourceOperator,
                                   SecretOperator secretOperations,
                                   PvcOperator pvcOperator,
                                   CronJobOperator cronJobOperator,
                                   PodOperator podOperator,
                                   EventOperator eventOperator,
                                   String caCertName, String caKeyName, String caNamespace) {

        super(vertx, assemblyType, certManager, resourceOperator);
        this.secretOperations = secretOperations;
        this.pvcOperator = pvcOperator;
        this.cronJobOperator = cronJobOperator;
        this.podOperator = podOperator;
        this.eventOperator = eventOperator;
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
     */
    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, ZookeeperBackup zookeeperBackup) {
        final String namespace = reconciliation.namespace();
        final String name = reconciliation.name();
        final Labels labels = Labels.fromResource(zookeeperBackup).withKind(zookeeperBackup.getKind());
        final String clusterName = labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);

        final Secret clusterCaCert = secretOperations.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperations.get(caNamespace, caKeyName);
        final Secret certSecret = secretOperations.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        final Future<Void> chain = Future.future();
        ZookeeperBackupModel zookeeperBackupModel;

        try {
            zookeeperBackupModel = new ZookeeperBackupModel(namespace, name, labels);
            zookeeperBackupModel.fromCrd(certManager, zookeeperBackup, clusterCaCert, clusterCaKey, certSecret);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }


        Secret desired = zookeeperBackupModel.getSecret();
        PersistentVolumeClaim desiredPvc = zookeeperBackupModel.getStorage();
        CronJob desiredCronJob = zookeeperBackupModel.getCronJob();


        CompositeFuture.join(
            secretOperations.reconcile(namespace, desired.getMetadata().getName(), desired),
            pvcOperator.reconcile(namespace, desiredPvc.getMetadata().getName(), desiredPvc))
            .compose(res -> cronJobOperator.reconcile(namespace, desiredCronJob.getMetadata().getName(), desiredCronJob))
            .compose(res -> watchContainerStatus(namespace, labels, kind, "burry"))
            .compose(state -> chain.complete(), chain);

        log.debug("{}: Updating ZookeeperBackup {} in namespace {}", reconciliation, name, namespace);

        return chain;

    }

    /**
     * Deletes the zookeeper backup
     *
     * @param reconciliation Reconciliation
     */
    @Override
    protected Future<Void> delete(Reconciliation reconciliation) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        log.debug("{}: Deleting ZookeeperBackup", reconciliation, name, namespace);

        return CompositeFuture.join(
            secretOperations.reconcile(namespace, name, null),
//            pvcOperator.reconcile(namespace, name, null), keep the storage TODO: Add condition based on deleteClaim.
            cronJobOperator.reconcile(namespace, name, null))
            .map((Void) null);

    }

    /**
     *      * TODO: move in dedicate class
     * @param namespace
     * @param labels
     * @param kind
     */
    protected Future<Void> watchContainerStatus(String namespace, Labels labels, String kind, String containerName) {

        Labels selector = labels.withKind(kind);
        List<Pod> pods = podOperator.list(namespace, selector).stream().sorted(
            Comparator.comparing(p -> p.getMetadata().getName())
        ).collect(Collectors.toList());

        if (pods.size() > 0) {
            final Pod pod = pods.get(0);
            final String name = pod.getMetadata().getName();
            log.info("Watching pod : {}", name);

            return podOperator.waitFor(namespace, name, 1000L, 60000L, (a, b) -> {
                ContainerStatus containerStatus = getContainerStatus(containerName, pod);
                return isTerminated(containerStatus);
            }).compose(res -> podOperator.reconcile(namespace, name, null))
                .compose(
                    res -> {
                        eventOperator.createEvent(namespace, EventUtils.createEvent(namespace, "backup-" + name, EventType.NORMAL,
                            "Backup completed", "Backed up", ZookeeperBackupOperator.class.getName(), pod));
                        return Future.succeededFuture();
                    });
        }
        return Future.succeededFuture();
    }

    /**
     *      * TODO: move in dedicate class
     * @param containerName
     * @param pod
     * @return
     */
    private ContainerStatus getContainerStatus(String containerName, Pod pod) {
        final List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();

        return containerStatuses.stream()
            .filter(container -> containerName.equals(container.getName()))
            .findAny()
            .orElse(null);
    }

    /**
     * @param containerStatus
     * @return
     */
    private boolean isTerminated(ContainerStatus containerStatus) {
        return containerStatus != null && containerStatus.getState() != null
            && containerStatus.getState().getTerminated() != null
            && containerStatus.getState().getTerminated().getExitCode() == 0;
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
        return Collections.EMPTY_LIST;
    }

    @Override
    public int getPort() {
        return HEALTH_SERVER_PORT;
    }

}
