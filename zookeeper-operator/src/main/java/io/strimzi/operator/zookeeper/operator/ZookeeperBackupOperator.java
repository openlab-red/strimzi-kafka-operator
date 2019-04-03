/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.Schedule;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.model.EventType;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.ResourceType;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.ResourceOperatorFacade;
import io.strimzi.operator.common.utils.EventUtils;
import io.strimzi.operator.zookeeper.model.ZookeeperBackupModel;
import io.strimzi.operator.zookeeper.model.ZookeeperOperatorResources;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

import static io.strimzi.operator.burry.model.BurryModel.BURRY;
import static io.strimzi.operator.burry.model.BurryModel.TLS_SIDECAR;

/**
 * Operator for a Zookeeper Backup.
 */
public class ZookeeperBackupOperator extends ZookeeperOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup, Resource<ZookeeperBackup, DoneableZookeeperBackup>> {

    private static final Logger log = LogManager.getLogger(ZookeeperBackupOperator.class.getName());
    private static final int HEALTH_SERVER_PORT = 8081;

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
    public ZookeeperBackupOperator(Vertx vertx,
                                   ResourceType assemblyType,
                                   CertManager certManager,
                                   CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> resourceOperator,
                                   ResourceOperatorFacade resourceOperatorFacade,
                                   String caCertName, String caKeyName, String caNamespace,
                                   ImagePullPolicy imagePullPolicy) {

        super(vertx, assemblyType, certManager, resourceOperator, resourceOperatorFacade, caCertName, caKeyName, caNamespace, imagePullPolicy);

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

        final Secret clusterCaCert = secretOperator.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperator.get(caNamespace, caKeyName);
        final Secret certSecret = secretOperator.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

        final Future<Void> chain = Future.future();
        ZookeeperBackupModel zookeeperBackupModel;

        try {
            zookeeperBackupModel = new ZookeeperBackupModel(namespace, name, labels, imagePullPolicy);
            zookeeperBackupModel.fromCrd(certManager, zookeeperBackup, clusterCaCert, clusterCaKey, certSecret);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }


        Secret desired = zookeeperBackupModel.getSecret();
        PersistentVolumeClaim desiredPvc = zookeeperBackupModel.getStorage();
        NetworkPolicy networkPolicy = zookeeperBackupModel.getNetworkPolicy();

        final Schedule schedule = zookeeperBackup.getSpec().getSchedule();
        final Future<ReconcileResult<PersistentVolumeClaim>> common =
            secretOperator.reconcile(namespace, desired.getMetadata().getName(), desired)
                .compose(res -> networkPolicyOperator.reconcile(namespace, networkPolicy.getMetadata().getName(), networkPolicy))
                .compose(res -> pvcOperator.reconcile(namespace, desiredPvc.getMetadata().getName(), desiredPvc));

        if (schedule.isAdhoc()) {
            Job desiredJob = zookeeperBackupModel.getJob();
            common
                .compose(res -> jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob))
                .compose(res -> resourceOperator.reconcile(namespace, name, null))
                .compose(state -> chain.complete(), chain);
        } else {
            CronJob desiredCronJob = zookeeperBackupModel.getCronJob();
            common
                .compose(res -> cronJobOperator.reconcile(namespace, desiredCronJob.getMetadata().getName(), desiredCronJob))
                .compose(state -> chain.complete(), chain);
        }
        log.debug("{}: Updating ZookeeperBackup {} in namespace {}", reconciliation, name, namespace);
        return chain;

    }

    /**
     * Deletes the zookeeper backup
     * Previous Jobs for adhoc execution are kept for history.
     *
     * @param reconciliation Reconciliation
     */
    @Override
    protected Future<Void> delete(Reconciliation reconciliation) {
        final String namespace = reconciliation.namespace();
        final String name = reconciliation.name();

        log.debug("{}: Deleting ZookeeperBackup", reconciliation, name, namespace);

        return deleteResourceWithName(cronJobOperator, namespace, name).map((Void) null);

    }

    /**
     * Watch Container
     *
     * @param action    Event
     * @param pod       Pod
     * @param name      name of the pod
     * @param namespace Namespace where to search for resources
     */
    @Override
    protected void containerAddModWatch(Watcher.Action action, Pod pod, String name, String namespace) {
        if (!pod.getStatus().getPhase().equals("Succeeded") && podOperator.isTerminated(BURRY, pod)) {
            log.info("{} {} in namespace {} was {}", kind, name, namespace, action);
            final Future<String> containerLog = podOperator.getContainerLog(namespace, name, BURRY);
            containerLog
                .compose(c -> podOperator.terminateContainer(namespace, name, TLS_SIDECAR))
                .compose(e -> eventOperator.createEvent(namespace, EventUtils.createEvent(namespace, "backup-" + name, EventType.NORMAL,
                    "Backup completed: " + containerLog, "Backed up", ZookeeperBackupOperator.class.getName(), pod)));
        }
    }

    /**
     * Gets all resources relevant to ZookeeperBackup
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
