/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.Schedule;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackupSpec;
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
import io.strimzi.operator.common.operator.resource.ReconcileResult;
import io.strimzi.operator.common.operator.resource.ResourceOperatorFacade;
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
import java.util.List;

import static io.strimzi.operator.burry.model.BurryModel.BURRY;
import static io.strimzi.operator.burry.model.BurryModel.TLS_SIDECAR;

/**
 * Operator for a Zookeeper Backup.
 */
public class ZookeeperBackupOperator extends AbstractBaseOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup, Resource<ZookeeperBackup, DoneableZookeeperBackup>> {

    private static final Logger log = LogManager.getLogger(ZookeeperBackupOperator.class.getName());
    private final SecretOperator secretOperator;
    private final PvcOperator pvcOperator;
    private final CronJobOperator cronJobOperator;
    private final PodOperator podOperator;
    private final EventOperator eventOperator;
    private final JobOperator jobOperator;
    private final String caCertName;
    private final String caKeyName;
    private final String caNamespace;
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
     */
    public ZookeeperBackupOperator(Vertx vertx,
                                   ResourceType assemblyType,
                                   CertManager certManager,
                                   CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> resourceOperator,
                                   ResourceOperatorFacade resourceOperatorFacade,
                                   String caCertName, String caKeyName, String caNamespace) {

        super(vertx, assemblyType, certManager, resourceOperator);
        this.secretOperator = resourceOperatorFacade.getSecretOperator();
        this.pvcOperator = resourceOperatorFacade.getPvcOperator();
        this.cronJobOperator = resourceOperatorFacade.getCronJobOperator();
        this.podOperator = resourceOperatorFacade.getPodOperator();
        this.eventOperator = resourceOperatorFacade.getEventOperator();
        this.jobOperator = resourceOperatorFacade.getJobOperator();
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

        final Secret clusterCaCert = secretOperator.get(caNamespace, caCertName);
        final Secret clusterCaKey = secretOperator.get(caNamespace, caKeyName);
        final Secret certSecret = secretOperator.get(namespace, ZookeeperOperatorResources.secretBackupName(clusterName));

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

        final Schedule schedule = zookeeperBackup.getSpec().getSchedule();
        final Future<ReconcileResult<PersistentVolumeClaim>> common =
            secretOperator.reconcile(namespace, desired.getMetadata().getName(), desired)
                .compose(res -> pvcOperator.reconcile(namespace, desiredPvc.getMetadata().getName(), desiredPvc));

        if (schedule.isAdhoc()) {
            Job desiredJob = zookeeperBackupModel.getJob();

            common
                .compose(res -> deleteResourceWithName(jobOperator, namespace, name)) // cleanup previous jobs
                .compose(res -> jobOperator.reconcile(namespace, desiredJob.getMetadata().getName(), desiredJob))
                .compose(res -> watchContainerStatus(namespace, name, labels.withKind(kind), zookeeperBackup))
                .compose(state -> chain.complete(), chain);
        } else {
            CronJob desiredCronJob = zookeeperBackupModel.getCronJob();

            common
                .compose(res -> cronJobOperator.reconcile(namespace, desiredCronJob.getMetadata().getName(), desiredCronJob))
                .compose(res -> !desiredCronJob.getSpec().getSuspend() ? watchContainerStatus(namespace, name, labels.withKind(kind), zookeeperBackup) : Future.succeededFuture())
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


        return CompositeFuture.join(
            deleteResourceWithName(secretOperator, namespace, name),
            deleteResourceWithName(cronJobOperator, namespace, name))
            .map((Void) null);

    }

    /**
     * Watch Container Status
     * TODO: maybe a separate watch in parallel with the operator
     *
     * @param namespace Namespace where to search for resources
     * @param selector  Labels which the resources should have
     * @return Future
     */
    protected Future<Void> watchContainerStatus(String namespace, String name, Labels selector, ZookeeperBackup zookeeperBackup) {
        final ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();
        return podOperator.waitContainerIsTerminated(namespace, selector, BURRY)
            .compose(pod -> {
                    if (pod != null) {
                        final String podName = pod.getMetadata().getName();
                        final Future<String> containerLog = podOperator.getContainerLog(namespace, podName, BURRY);

                        containerLog
                            .compose(c -> podOperator.terminateContainer(namespace, podName, TLS_SIDECAR))
                            .compose(e -> eventOperator.createEvent(namespace, EventUtils.createEvent(namespace, "backup-" + podName, EventType.NORMAL,
                                "Backup completed: " + containerLog, "Backed up", ZookeeperBackupOperator.class.getName(), pod)))
                            .compose(b -> zookeeperBackupSpec.getSchedule().isAdhoc() ? resourceOperator.reconcile(namespace, name, null) : Future.succeededFuture());
                    }
                    log.debug("{}: Pod not found", selector);
                    return Future.succeededFuture();
                }
            );
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
