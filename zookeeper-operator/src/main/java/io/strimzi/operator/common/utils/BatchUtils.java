/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.common.utils;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.strimzi.operator.common.model.ConcurrencyPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.RestartPolicy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class BatchUtils {

    private BatchUtils() {
    }

    private static final Logger log = LogManager.getLogger(BatchUtils.class.getName());

    public static CronJob buildCronJob(String name, String namespace, Labels labels, String schedule, Boolean suspend, List<Container> containers, List<Volume> volumes) {
        CronJobBuilder cronJobBuilder = new CronJobBuilder().withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
            // TODO: ConcurrencyPolicy.REPLACE  becessary due the sidecar container, change to FORBID and watch containerStatus from Operator
            .withConcurrencyPolicy(ConcurrencyPolicy.FORBID.toString())
            .withSchedule(schedule)
            .withSuspend(suspend)
            //checks how many missed schedules happened in the last 200 seconds (ie, 3 missed schedules), rather than from the last scheduled time until now.
            .withStartingDeadlineSeconds(200L)

            //JobTemplate
            .withNewJobTemplate()

            //Pod Template
            .withNewSpec().withNewTemplate().withNewMetadata()
            .withLabels(labels.toMap())
            .addToLabels(Labels.STRIMZI_DOMAIN + "cronjobs", name)
            .endMetadata()
            .withNewSpec()

            //Container
            .withContainers(containers)

            //Volume
            .withVolumes(volumes)

            .withRestartPolicy(RestartPolicy.NEVER.toString())
            .endSpec()

            .endTemplate()
            .endSpec()
            .endJobTemplate()
            .endSpec();

        return cronJobBuilder.build();
    }

    public static Job buildJob(String name, String namespace, Labels labels, List<Container> containers, List<Volume> volumes) {
        JobBuilder jobBuilder = new JobBuilder().withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
            .withNewTemplate()
            .withNewMetadata()
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()

            //Container
            .withContainers(containers)

            //Volume
            .withVolumes(volumes)

            .withRestartPolicy(RestartPolicy.NEVER.toString())
            .endSpec()

            .endTemplate()

            .endSpec();

        return jobBuilder.build();

    }
}
