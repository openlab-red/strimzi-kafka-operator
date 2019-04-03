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
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
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
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.AbstractBaseOperator;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.EventOperator;
import io.strimzi.operator.common.operator.resource.JobOperator;
import io.strimzi.operator.common.operator.resource.NetworkPolicyOperator;
import io.strimzi.operator.common.operator.resource.PodOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
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
import static io.strimzi.operator.zookeeper.ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT;

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
    private final NetworkPolicyOperator networkPolicyOperator;
    private final PvcOperator pvcOperator;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
    private static final int HEALTH_SERVER_PORT = 8082;

    public static final int POLL_INTERVAL = 5_000;

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
    public ZookeeperRestoreOperator(Vertx vertx,
                                    ResourceType assemblyType,
                                    CertManager certManager,
                                    CrdOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore> resourceOperator,
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
        this.statefulSetOperator = resourceOperatorFacade.getStatefulSetOperator();
        this.networkPolicyOperator = resourceOperatorFacade.getNetworkPolicyOperator();
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
        final String name = reconciliation.name();
        final Labels labels = Labels.fromResource(zookeeperRestore).withKind(zookeeperRestore.getKind());
        final String clusterName = labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);
        final Secret clusterCaCert = secretOperator.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperator.get(caNamespace, caKeyName);
        final Secret restoreSecret = secretOperator.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        final Future<CompositeFuture> chain = Future.future();
        ZookeeperRestoreModel zookeeperRestoreModel;
        try {
            zookeeperRestoreModel = new ZookeeperRestoreModel(namespace, name, labels, cronJobOperator, imagePullPolicy);
            zookeeperRestoreModel.fromCrd(certManager, zookeeperRestore, clusterCaCert, clusterCaKey, restoreSecret);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        Secret desired = zookeeperRestoreModel.getSecret();
        Job desiredJob = zookeeperRestoreModel.getJob();
        NetworkPolicy networkPolicy = zookeeperRestoreModel.getNetworkPolicy();

        StatefulSet zookeeperStatefulSet = statefulSetOperator.get(namespace, KafkaResources.zookeeperStatefulSetName(clusterName));
        int zookeeperReplicas = zookeeperStatefulSet.getSpec().getReplicas();

        StatefulSet kafkaStatefulSet = statefulSetOperator.get(namespace, KafkaResources.kafkaStatefulSetName(clusterName));
        int kafkaReplicas = kafkaStatefulSet.getSpec().getReplicas();

        log.debug("{}: Updating ZookeeperRestore {} in namespace {}", reconciliation, name, namespace);

        final boolean full = zookeeperRestore.getSpec().getRestore().isFull();
        log.info("{}: Starting ZookeeperRestore {} full: {}, in namespace {} ", reconciliation, name, full, namespace);

        // Job are immutable, this should always empty operation unless using the same snapshot over and over
        final Future<ReconcileResult<NetworkPolicy>> common = jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), null)
            .compose(res -> secretOperator.reconcile(namespace, desired.getMetadata().getName(), desired))
            .compose(res -> networkPolicyOperator.reconcile(namespace, networkPolicy.getMetadata().getName(), networkPolicy));

        if (full) {
            common
                .compose(res -> deleteZkPersistentVolumeClaim(namespace, clusterName))
                .compose(res -> statefulSetOperator.scaleDown(namespace, zookeeperStatefulSet.getMetadata().getName(), 0))
                .compose(res -> statefulSetOperator.scaleDown(namespace, kafkaStatefulSet.getMetadata().getName(), 0))
                .compose(res -> statefulSetOperator.scaleUp(namespace, zookeeperStatefulSet.getMetadata().getName(), zookeeperReplicas))
                .compose(res -> statefulSetOperator.podReadiness(namespace, zookeeperStatefulSet, POLL_INTERVAL, STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT))
                .compose(res -> statefulSetOperator.scaleUp(namespace, kafkaStatefulSet.getMetadata().getName(), kafkaReplicas))
                .compose(res -> statefulSetOperator.podReadiness(namespace, kafkaStatefulSet, POLL_INTERVAL, STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT))
                .compose(res -> jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob))
                .compose(res -> watchContainerStatus(namespace, labels.withKind(kind), zookeeperRestore, kafkaStatefulSet))
                .compose(state -> chain.complete(), chain);
        } else {
            common
                .compose(res -> jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob))
                .compose(res -> watchContainerStatus(namespace, labels.withKind(kind), zookeeperRestore, kafkaStatefulSet))
                .compose(state -> chain.complete(), chain);
        }
        return chain.map((Void) null);
    }

    /**
     * Delete zookeeper persistent Volume Claim.
     *
     * @param namespace   Namespace where to search for resources
     * @param clusterName cluster name
     * @return Future
     */
    private Future<CompositeFuture> deleteZkPersistentVolumeClaim(String namespace, String clusterName) {
        String zkSsName = KafkaResources.zookeeperStatefulSetName(clusterName);
        Labels pvcSelector = Labels.forCluster(clusterName).withKind(Kafka.RESOURCE_KIND).withName(zkSsName);
        List<PersistentVolumeClaim> pvcs = pvcOperator.list(namespace, pvcSelector);
        List<Future> result = new ArrayList<>();

        for (PersistentVolumeClaim pvc : pvcs) {
            log.debug("Delete selected PVC {} with labels {}", pvc.getMetadata().getName(), pvcSelector);
            pvc.getMetadata().setFinalizers(null); // force delete
            result.add(
                pvcOperator.reconcile(namespace, pvc.getMetadata().getName(), null)
                    .compose(res -> pvcOperator.reconcile(namespace, pvc.getMetadata().getName(), pvc)));
        }
        return CompositeFuture.join(result)
            .compose(res -> pvcOperator.waitFor(namespace, "data-zookeeper-*", POLL_INTERVAL, STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT, (a, b) -> pvcOperator.list(namespace, pvcSelector).size() == 0))
            .compose(res -> restoreZkPersistentVolumeClaim(namespace, pvcs));
    }

    /**
     * Restore zookeeper persistent Volume Claim.
     *
     * @param namespace Namespace where to search for resources
     * @param pvcs      List of Persistent volume claim
     * @return Future
     */
    private Future<CompositeFuture> restoreZkPersistentVolumeClaim(String namespace, List<PersistentVolumeClaim> pvcs) {
        List<Future> result = new ArrayList<>();


        for (PersistentVolumeClaim pvc : pvcs) {
            log.debug("Restore selected PVC {} with labels {}", pvc.getMetadata().getName());

            pvc.getSpec().setVolumeName(null);
            pvc.getMetadata().setAnnotations(null);
            pvc.getMetadata().setDeletionGracePeriodSeconds(null);
            pvc.getMetadata().setDeletionTimestamp(null);
            pvc.getMetadata().setCreationTimestamp(null);
            pvc.getMetadata().setResourceVersion(null);
            pvc.getMetadata().setUid(null);
            pvc.getMetadata().setSelfLink(null);
            pvc.setStatus(null);

            result.add(pvcOperator.reconcile(namespace, pvc.getMetadata().getName(), pvc));
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

        return Future.succeededFuture();
    }

    /**
     * Watch Container Status
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return Future
     */
    protected Future<Void> watchContainerStatus(String namespace, Labels selector, ZookeeperRestore
        zookeeperRestore, StatefulSet kafkaStatefulSet) {
        int kafkaReplicas = kafkaStatefulSet.getSpec().getReplicas();

        return podOperator.waitContainerIsTerminated(namespace, selector, BURRY)
            .compose(pod -> {
                if (pod != null) {
                    final String name = pod.getMetadata().getName();
                    resourceOperator.reconcile(namespace, zookeeperRestore.getMetadata().getName(), null)
                        .compose(res -> podOperator.terminateContainer(namespace, name, TLS_SIDECAR))
                        .compose(res -> statefulSetOperator.scaleDown(namespace, kafkaStatefulSet.getMetadata().getName(), kafkaReplicas))
                        .compose(res -> statefulSetOperator.scaleUp(namespace, kafkaStatefulSet.getMetadata().getName(), kafkaReplicas))
                        .compose(res -> statefulSetOperator.podReadiness(namespace, kafkaStatefulSet, POLL_INTERVAL, STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT))
                        .compose(res -> eventOperator.createEvent(namespace, EventUtils.createEvent(namespace, "restore-" + name, EventType.NORMAL,
                            "Restore snapshot ID:" + zookeeperRestore.getSpec().getSnapshot().getId() + " completed", "Restored", ZookeeperRestoreOperator.class.getName(), pod)))
                        .compose(res -> Future.succeededFuture());
                } else {
                    log.debug("{}: Pod not found", selector);
                }
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
