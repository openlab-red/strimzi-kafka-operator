/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.CustomResource;

public interface BatchModel<T extends CustomResource> extends AppModel<T> {

    void addCronJob(T customResource);

    CronJob getCronJob();

    void addJob(T customResource);

    Job getJob();

}


