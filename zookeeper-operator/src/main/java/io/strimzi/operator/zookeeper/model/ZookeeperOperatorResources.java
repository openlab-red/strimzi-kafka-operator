/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

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
    public static String jobsRestoreName(String clusterName, String snapshotId) {
        return clusterName + "-restore-" + snapshotId + "-job";
    }

    /**
     * Returns the name of the Job for the ZookeeperBackupOperator in Ad Hoc mode
     *
     * @param clusterName The {@code metadata.name} of the {@code Kafka} resource.
     * @return The name of the corresponding Job name.
     */
    public static String jobsBackupAdHocName(String clusterName) {
        return clusterName + "-backup-adhoc-" + System.currentTimeMillis() + "-job";
    }
}
