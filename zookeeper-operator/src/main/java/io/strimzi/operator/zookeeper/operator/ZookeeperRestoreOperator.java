/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.ZookeeperRestoreList;
import io.strimzi.api.kafka.model.DoneableZookeeperRestore;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.EventType;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.AbstractBaseOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.EventOperator;
import io.strimzi.operator.common.operator.resource.JobOperator;
import io.strimzi.operator.common.operator.resource.PodOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.ResourceOperatorFacade;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.operator.resource.SimpleStatefulSetOperator;
import io.strimzi.operator.common.utils.EventUtils;
import io.strimzi.operator.zookeeper.model.ZookeeperOperatorResources;
import io.strimzi.operator.zookeeper.model.ZookeeperRestoreModel;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.strimzi.operator.burry.model.BurryModel.BURRY;
import static io.strimzi.operator.burry.model.BurryModel.TLS_SIDECAR;

/**
 * Operator for a Zookeeper Restore.
 */
public class ZookeeperRestoreOperator extends AbstractBaseOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore, Resource<ZookeeperRestore, DoneableZookeeperRestore>> {

    private static final Logger log = LogManager.getLogger(ZookeeperRestoreOperator.class.getName());
    private final SecretOperator secretOperator;
    private final JobOperator jobOperator;
    private final PodOperator podOperator;
    private final EventOperator eventOperator;
    private final CronJobOperator cronJobOperator;
    private final SimpleStatefulSetOperator statefulSetOperator;
    private final PvcOperator pvcOperator;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
    private static final int HEALTH_SERVER_PORT = 8082;

    public static final int POLL_INTERVAL = 1_000;
    public static final int OPERATION_TIMEOUT_MS = 120_000;

    /**
     * @param vertx                  The Vertx instance
     * @param assemblyType           The resource type
     * @param certManager            For managing certificates
     * @param resourceOperator       For operating on Custom Resources
     * @param resourceOperatorFacade For operating on Kubernetes Resource
     * @param caCertName             The name of the Secret containing the cluster CA certificate
     * @param caKeyName              The name of the Secret containing the cluster CA private key
     * @param caNamespace            The namespace of the Secret containing the cluster CA
     */
    public ZookeeperRestoreOperator(Vertx vertx,
                                    ResourceType assemblyType,
                                    CertManager certManager,
                                    CrdOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore> resourceOperator,
                                    ResourceOperatorFacade resourceOperatorFacade,
                                    String caCertName, String caKeyName, String caNamespace) {

        super(vertx, assemblyType, certManager, resourceOperator);
        this.secretOperator = resourceOperatorFacade.getSecretOperator();
        this.pvcOperator = resourceOperatorFacade.getPvcOperator();
        this.cronJobOperator = resourceOperatorFacade.getCronJobOperator();
        this.podOperator = resourceOperatorFacade.getPodOperator();
        this.eventOperator = resourceOperatorFacade.getEventOperator();
        this.jobOperator = resourceOperatorFacade.getJobOperator();
        this.statefulSetOperator = resourceOperatorFacade.getStatefulSetOperator();
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
     */

    @Override
    protected Future<Void> createOrUpdate(Reconciliation reconciliation, ZookeeperRestore zookeeperRestore) {
        final String namespace = reconciliation.namespace();
        final String clusterName = reconciliation.name();
        final Labels labels = Labels.fromResource(zookeeperRestore).withKind(zookeeperRestore.getKind());
        final Secret clusterCaCert = secretOperator.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperator.get(caNamespace, caKeyName);
        final Secret restoreSecret = secretOperator.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        final Future<CompositeFuture> chain = Future.future();
        ZookeeperRestoreModel zookeeperRestoreModel;
        try {
            zookeeperRestoreModel = new ZookeeperRestoreModel(namespace, clusterName, labels, statefulSetOperator, cronJobOperator);
            zookeeperRestoreModel.fromCrd(certManager, zookeeperRestore, clusterCaCert, clusterCaKey, restoreSecret);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        Secret desired = zookeeperRestoreModel.getSecret();
        Job desiredJob = zookeeperRestoreModel.getJob();
        StatefulSet zookeeperStatefulSet = statefulSetOperator.get(namespace, KafkaResources.zookeeperStatefulSetName(clusterName));
        int zookeeperReplicas = zookeeperStatefulSet.getSpec().getReplicas();

        StatefulSet kafkaStatefulSet = statefulSetOperator.get(namespace, KafkaResources.kafkaStatefulSetName(clusterName));
        int kafkaReplicas = zookeeperStatefulSet.getSpec().getReplicas();

        log.debug("{}: Updating ZookeeperRestore {} in namespace {}", reconciliation, clusterName, namespace);

        // Job are immutable, this should always empty operation unless using the same snapshot over and over
        jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), null).compose(job -> {

            final boolean full = zookeeperRestore.getSpec().getRestore().isFull();
            log.info("{}: Starting ZookeeperRestore {} full: {}, in namespace {} ", reconciliation, clusterName, full, namespace);

            if (full) {
                return secretOperator.reconcile(namespace, desired.getMetadata().getName(), desired)
                    .compose(res -> statefulSetOperator.scaleDown(namespace, zookeeperStatefulSet.getMetadata().getName(), 0))
                    .compose(res -> statefulSetOperator.scaleDown(namespace, kafkaStatefulSet.getMetadata().getName(), 0))
                    .compose(res -> deleteZkPersistentVolumeClaim(namespace, clusterName).map((Void) null))
                    .compose(res -> statefulSetOperator.scaleUp(namespace, zookeeperStatefulSet.getMetadata().getName(), zookeeperReplicas))
                    .compose(res -> statefulSetOperator.podReadiness(namespace, zookeeperStatefulSet, POLL_INTERVAL, OPERATION_TIMEOUT_MS))
                    .compose(res -> statefulSetOperator.scaleUp(namespace, kafkaStatefulSet.getMetadata().getName(), kafkaReplicas))
                    .compose(res -> statefulSetOperator.podReadiness(namespace, kafkaStatefulSet, POLL_INTERVAL, OPERATION_TIMEOUT_MS))
                    .compose(res -> jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob))
                    .compose(res -> watchContainerStatus(namespace, labels.withKind(kind), zookeeperRestore))
                    .compose(state -> chain.complete(), chain);
            } else {
                return CompositeFuture.join(
                    secretOperator.reconcile(namespace, desired.getMetadata().getName(), desired),
                    jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob))
                    .compose(res -> watchContainerStatus(namespace, labels.withKind(kind), zookeeperRestore))
                    .compose(state -> chain.complete(), chain);
            }
        });
        return chain.map((Void) null);

    }

    private Future<CompositeFuture> deleteZkPersistentVolumeClaim(String namespace, String clusterName) {
        String zkSsName = KafkaResources.zookeeperStatefulSetName(clusterName);
        Labels pvcSelector = Labels.forCluster(clusterName).withKind(Kafka.RESOURCE_KIND).withName(zkSsName);
        List<PersistentVolumeClaim> pvcs = pvcOperator.list(namespace, pvcSelector);
        List<Future> result = new ArrayList<>();

        for (PersistentVolumeClaim pvc : pvcs) {
            log.debug("Delete selected PVCs with labels", pvcSelector);
            result.add(pvcOperator.reconcile(namespace, pvc.getMetadata().getName(), null));
        }
        return CompositeFuture.join(result);
    }


    /**
     * Deletes the zookeeper restore
     * Previous Jobs are kept for history.
     *
     * @param reconciliation Reconciliation
     */
    @Override
    protected Future<Void> delete(Reconciliation reconciliation) {
        String namespace = reconciliation.namespace();
        String name = reconciliation.name();
        log.debug("{}: Deleting ZookeeperRestore", reconciliation, name, namespace);

        // TODO: is it necessary to remove the secret each time ?
        return secretOperator.reconcile(namespace, ZookeeperOperatorResources.secretRestoreName(name), null).map((Void) null);
    }

    /**
     * @param namespace
     * @param selector
     * @return
     */
    protected Future<Void> watchContainerStatus(String namespace, Labels selector, ZookeeperRestore zookeeperRestore) {
        return podOperator.waitContainerIsTerminated(namespace, selector, BURRY)
            .compose(pod -> {
                if (pod != null) {
                    final String name = pod.getMetadata().getName();
                    podOperator.terminateContainer(namespace, name, TLS_SIDECAR)
                        .compose(res -> resourceOperator.reconcile(namespace, zookeeperRestore.getMetadata().getName(), null))
                        .compose(res -> {
                            eventOperator.createEvent(namespace, EventUtils.createEvent(namespace, "restore-" + name, EventType.NORMAL,
                                "Restore snapshot ID:" + zookeeperRestore.getSpec().getSnapshot().getId() + " completed", "Restored", ZookeeperRestoreOperator.class.getName(), pod));

                            return Future.succeededFuture();
                        });
                }
                log.debug("{}: Pod not found", selector);
                return Future.succeededFuture();
            });
    }

    /**
     * Gets all resources relevant to ZookeeperRestore
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return List
     */
    @Override
    protected List<HasMetadata> getResources(String namespace, Labels selector) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public int getPort() {
        return HEALTH_SERVER_PORT;
    }
}
