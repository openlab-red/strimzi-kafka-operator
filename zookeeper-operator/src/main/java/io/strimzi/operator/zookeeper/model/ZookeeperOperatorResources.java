/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import java.time.Instant;

/**
 * Encapsulates the naming scheme used for the resources which the Zookeeper Operator manages
 */
public class ZookeeperOperatorResources {

    private ZookeeperOperatorResources() {
    }

    /**
     * Returns the name of the CronJob for the ZookeeperBackupOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @return The name of the corresponding CronJob name.
     */
    public static String cronJobsBackupName(String clusterName) {
        return clusterName + "-backup-cronjob";
    }


    /**
     * Returns the name of the Persistent Volume Claim for the ZookeeperBackupOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @return The name of the corresponding Persistent Volume Claim name.
     */
    public static String persistentVolumeClaimBackupName(String clusterName) {
        return clusterName + "-backup-storage";
    }

    /**
     * Returns the name of the Secret for the ZookeeperBackupOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @return The name of the corresponding Secret name.
     */
    public static String secretBackupName(String clusterName) {
        return clusterName + "-backup-certs";
    }


    /**
     * Returns the name of the Secret for the ZookeeperRestoreOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @return The name of the corresponding Secret name.
     */
    public static String secretRestoreName(String clusterName) {
        return clusterName + "-restore-certs";
    }


    /**
     * Returns the name of the Job for the ZookeeperRestoreOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @param snapshotId  The backup Id.
     * @return The name of the corresponding Job name.
     */
    public static String jobsRestoreName(String clusterName, Integer snapshotId) {
        return clusterName + "-restore-" + snapshotId + "-job";
    }

    /**
     * Returns the name of the Job for the ZookeeperBackupOperator in Ad Hoc mode
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @return The name of the corresponding Job name.
     */
    public static String jobsBackupAdHocName(String clusterName) {
        return clusterName + "-backup-adhoc-" + Instant.now().getEpochSecond() / 60 + "-job";
    }

    /**
     * Returns the name of the NetworkPolicy for the ZookeeperOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @param kind        of ZookeeperOperator
     * @return The name of the networkpolicy
     */
    public static String networkPolicyName(String clusterName, String kind) {
        return clusterName + "-network-policy" + "-" + kind;
    }


    /**
     * Returns the name of the burry Manifest secret for the ZookeeperOperator
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @param type        of Storage
     * @return the name of the burry manifest secret
     */
    public static String burrySecretManifestName(String clusterName, String type) {
        return clusterName + "-" + type + "-burry-manifest";
    }


}
