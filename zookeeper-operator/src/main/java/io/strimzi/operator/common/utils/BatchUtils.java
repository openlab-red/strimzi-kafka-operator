/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.common.utils;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.CronJobSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.JobSpec;
import io.fabric8.kubernetes.api.model.batch.JobSpecBuilder;
import io.strimzi.operator.common.model.ConcurrencyPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.RestartPolicy;

import java.util.List;

public class BatchUtils {

    private BatchUtils() {
    }

    /**
     * Build a CronJob
     *
     * @param name       name of the Cron Job
     * @param namespace  namespace where the cronjob is created
     * @param labels     labels associated
     * @param schedule   cron expression
     * @param suspend    if it suspend or not
     * @param containers list of container
     * @param volumes    list of volume
     * @return CronJob definition
     */
    public static CronJob buildCronJob(String name, String namespace, Labels labels, String schedule, Boolean suspend, List<Container> containers, List<Volume> volumes) {
        return buildCronJob(name, namespace, labels, schedule, suspend, buildPodSpec(containers, volumes));
    }

    /**
     * Build a CronJob
     *
     * @param name      name of the Cron Job
     * @param namespace namespace where the cronjob is created
     * @param labels    labels associated
     * @param schedule  cron expression
     * @param suspend   if it suspend or not
     * @param podSpec   Pod specification for the cron job
     * @return CronJob definition
     */
    public static CronJob buildCronJob(String name, String namespace, Labels labels, String schedule, Boolean suspend, PodSpec podSpec) {
        return new CronJobBuilder()

            // Metadata
            .withMetadata(buildObjectMeta(name, namespace, labels))

            // CronJob spec
            .withSpec(buildCronJobSpec(schedule, suspend))
            .editSpec()

            //JobTemplate
            .withNewJobTemplate()
            .withSpec(buildJobSpec(labels, podSpec))
            .endJobTemplate()
            .endSpec()

            .build();
    }

    /**
     * @param name       name of the Cron Job
     * @param namespace  namespace where the cronjob is created
     * @param labels     labels associated
     * @param containers list of container
     * @param volumes    list of volume
     * @return Job definition
     */
    public static Job buildJob(String name, String namespace, Labels labels, List<Container> containers, List<Volume> volumes) {
        return buildJob(name, namespace, labels, buildPodSpec(containers, volumes));

    }

    /**
     * @param name      name of the Cron Job
     * @param namespace namespace where the cronjob is created
     * @param labels    labels associated
     * @param podSpec   Pod specification for the job
     * @return Job definition
     */
    public static Job buildJob(String name, String namespace, Labels labels, PodSpec podSpec) {
        return new JobBuilder()
            // Metadata
            .withMetadata(buildObjectMeta(name, namespace, labels))

            // JobSpec
            .withSpec(buildJobSpec(labels, podSpec))

            .build();
    }

    public static PodSpec buildPodSpec(List<Container> containers, List<Volume> volumes) {
        return new PodSpecBuilder()
            // Container
            .withContainers(containers)

            // Volume
            .withVolumes(volumes)

            // Policy
            .withRestartPolicy(RestartPolicy.NEVER.toString())
            .build();
    }

    public static JobSpec buildJobSpec(Labels labels, PodSpec podSpec) {
        return new JobSpecBuilder()
            .withNewTemplate()
            .withNewMetadata()
            .withLabels(labels.toMap())
            .endMetadata()
            .withSpec(podSpec)
            .endTemplate()
            .build();
    }

    public static CronJobSpec buildCronJobSpec(String schedule, Boolean suspend) {
        return new CronJobSpecBuilder()
            .withConcurrencyPolicy(ConcurrencyPolicy.FORBID.toString())
            .withSchedule(schedule)
            .withSuspend(suspend)
            //checks how many missed schedules happened in the last 200 seconds (ie, 3 missed schedules), rather than from the last scheduled time until now.
            .withStartingDeadlineSeconds(200L)
            .build();
    }


    public static ObjectMeta buildObjectMeta(String name, String namespace, Labels labels) {
        return new ObjectMetaBuilder().withName(name)
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .build();
    }
}
