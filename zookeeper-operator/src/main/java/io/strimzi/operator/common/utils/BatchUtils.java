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

    public static CronJob buildCronJob(String name, String namespace, Labels labels, String schedule, List<Container> containers, List<Volume> volumes) {
        CronJobBuilder cronJobBuilder = new CronJobBuilder().withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
            .withConcurrencyPolicy(ConcurrencyPolicy.REPLACE.toString()) // necessary due the sidecar container TODO: watch containerStatus from Operator
            .withSchedule(schedule)

            //JobTemplate
            .withNewJobTemplate()

            //Pod Template
            .withNewSpec().withNewTemplate().withNewMetadata()
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()

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

    public static Job buildJob(String name, String namespace, Labels labels) {
        JobBuilder jobBuilder = new JobBuilder().withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()

            .endSpec();

        return jobBuilder.build();

    }
}