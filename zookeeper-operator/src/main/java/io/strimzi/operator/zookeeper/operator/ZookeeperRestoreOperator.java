/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
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
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.ResourceOperatorFacade;
import io.strimzi.operator.common.operator.resource.SimpleStatefulSetOperator;
import io.strimzi.operator.common.utils.EventUtils;
import io.strimzi.operator.zookeeper.model.ZookeeperRestoreModel;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.strimzi.operator.burry.model.BurryModel.BURRY;
import static io.strimzi.operator.burry.model.BurryModel.TLS_SIDECAR;
import static io.strimzi.operator.zookeeper.ZookeeperOperatorConfig.STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT;

/**
 * Operator for a Zookeeper Restore.
 */
public class ZookeeperRestoreOperator extends ZookeeperOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore, Resource<ZookeeperRestore, DoneableZookeeperRestore>> {

    private static final Logger log = LogManager.getLogger(ZookeeperRestoreOperator.class.getName());
    private final SimpleStatefulSetOperator statefulSetOperator;
    private static final int HEALTH_SERVER_PORT = 8082;
    private static final int POLL_INTERVAL = 5_000;

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

        super(vertx, assemblyType, certManager, resourceOperator, resourceOperatorFacade, caCertName, caKeyName, caNamespace, imagePullPolicy);
        this.statefulSetOperator = resourceOperatorFacade.getStatefulSetOperator();

    }

    /**
     * Creates or updates the zookeeper restore. The implementation
     * should not assume that any resources are in any particular state (e.g. that the absence on
     * one resource means that all resources need to be created).
     *
     * @param reconciliation   Unique identification for the reconciliation
     * @param zookeeperRestore ZookeeperRestore resources with the desired zookeeper backup configuration.
     * @param namespace        Namespace where to search for resources
     * @param name             resource name
     * @param clusterName      cluster name
     * @param labels           resource labels
     * @param clusterCaCert    Secret containing the cluster CA certificate
     * @param clusterCaKey     Secret containing the cluster CA private key
     * @param restoreSecret    Secret containing the cluster CA
     * @return Future
     */
    @Override
    protected Future<Void> workflow(Reconciliation reconciliation, ZookeeperRestore zookeeperRestore, String namespace, String name, String clusterName, Labels labels, Secret clusterCaCert, Secret clusterCaKey, Secret restoreSecret) {
        final Future<Void> chain = Future.future();
        ZookeeperRestoreModel zookeeperRestoreModel;
        try {
            zookeeperRestoreModel = new ZookeeperRestoreModel(namespace, name, labels, cronJobOperator, imagePullPolicy);
            zookeeperRestoreModel.fromCrd(certManager, zookeeperRestore, clusterCaCert, clusterCaKey, restoreSecret);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        Secret desired = zookeeperRestoreModel.getSecret();

        Job desiredJob = zookeeperRestoreModel.getJob();
        final String jobName = desiredJob.getMetadata().getName();

        final Map<String, String> selector = new HashMap<>(labels.toMap());
        selector.put("job-name", jobName);

        if (!isRunning(namespace, Labels.fromMap(selector))) { // avoid parallel jobs
            NetworkPolicy networkPolicy = zookeeperRestoreModel.getNetworkPolicy();

            StatefulSet zookeeperStatefulSet = statefulSetOperator.get(namespace, KafkaResources.zookeeperStatefulSetName(clusterName));
            int zookeeperReplicas = zookeeperStatefulSet.getSpec().getReplicas();

            StatefulSet kafkaStatefulSet = statefulSetOperator.get(namespace, KafkaResources.kafkaStatefulSetName(clusterName));
            int kafkaReplicas = kafkaStatefulSet.getSpec().getReplicas();


            final boolean full = zookeeperRestore.getSpec().getRestore().isFull();
            log.info("{}: Starting ZookeeperRestore {} full: {}, in namespace {} ", reconciliation, name, full, namespace);

            // Job are immutable, this should always empty operation unless using the same snapshot over and over
            final Future<ReconcileResult<NetworkPolicy>> common = jobOperator.reconcile(namespace, jobName, null)
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
                    .compose(res -> jobOperator.reconcile(namespace, jobName, desiredJob))
                    .compose(res -> resourceOperator.reconcile(namespace, zookeeperRestore.getMetadata().getName(), null))
                    .compose(state -> chain.complete(), chain);

            } else {
                common
                    .compose(res -> jobOperator.reconcile(namespace, jobName, desiredJob))
                    .compose(state -> chain.complete(), chain);
            }
        } else {
            Future.succeededFuture().compose(state -> chain.complete(), chain);
        }
        return chain;
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
    private Future<CompositeFuture> restoreZkPersistentVolumeClaim(String
                                                                       namespace, List<PersistentVolumeClaim> pvcs) {
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


    @Override
    protected void containerAddModWatch(Watcher.Action action, Pod pod, String name, String namespace) {
        if (!pod.getStatus().getPhase().equals("Succeeded") && podOperator.isTerminated(BURRY, pod)) {
            final String clusterName = Labels.cluster(pod);
            final String kafkaStatefulSetName = KafkaResources.kafkaStatefulSetName(clusterName);
            final StatefulSet kafkaStatefulSet = statefulSetOperator.get(namespace, kafkaStatefulSetName);
            final int kafkaReplicas = kafkaStatefulSet.getSpec().getReplicas();
            final String[] split = name.split("-");
            final String snapshotId = split[split.length - 3];

            podOperator.terminateContainer(namespace, name, TLS_SIDECAR)
                .compose(res -> eventOperator.createEvent(namespace, EventUtils.createEvent(namespace, "restore-" + name, EventType.NORMAL,
                    "Restore snapshot ID:" + snapshotId + " completed", "Restored", ZookeeperRestoreOperator.class.getName(), pod)))
                .compose(res -> statefulSetOperator.scaleDown(namespace, kafkaStatefulSetName, 0))
                .compose(res -> statefulSetOperator.scaleUp(namespace, kafkaStatefulSetName, kafkaReplicas))
                .compose(res -> statefulSetOperator.podReadiness(namespace, kafkaStatefulSet, POLL_INTERVAL, STRIMZI_ZOOKEEPER_OPERATOR_RESTORE_TIMEOUT))
                .compose(res -> Future.succeededFuture());
        }
    }

    @Override
    public int getPort() {
        return HEALTH_SERVER_PORT;
    }
}
