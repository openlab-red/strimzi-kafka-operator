/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.zookeeper.utils;

import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobBuilder;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BatchUtils {

    private BatchUtils() {}

    private static final Logger log = LogManager.getLogger(BatchUtils.class.getName());

    public static CronJob buildCronJob(String name, String namespace, Labels labels) {
        CronJobBuilder cronJobBuilder = new CronJobBuilder().withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
            .withSchedule("*/5 * * * *")

            //JobTemplate
            .withNewJobTemplate()

            //Pod Template
            .withNewSpec().withNewTemplate().withNewMetadata()
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
            .withContainers()

            //TLS sidecar
            .addNewContainer()
            .withName("tls-sidecar")
            .addNewEnv()
            .withName("KAFKA_ZOOKEEPER_CONNECT")
            .withValue("msch-zookeeper-client:2181")
            .endEnv()
            .addNewEnv()
            .withName("TLS_SIDECAR_LOG_LEVEL")
            .withValue("notice")
            .endEnv()
            .addNewEnv()
            .withName("KAFKA_CERTS_NAME")
            .withValue(ZookeeperOperatorConfig.ZOOKEEPER_BACKUP_CERT_NAME)
            .endEnv()
            .withImagePullPolicy("IfNotPresent")
            .withImage("openlabred/burry-stunnel:v1.0.0")
            .withVolumeMounts(VolumeUtils.buildVolumeMount("burry", "/etc/tls-sidecar/burry/"),
                VolumeUtils.buildVolumeMount("cluster-ca", "/etc/tls-sidecar/cluster-ca-certs/"))
            .endContainer()

            // BURRY
            .addNewContainer()
            .withName("burry")
            .withImage("openlabred/burry:v1.0.2")
            .withArgs("--endpoint=127.0.0.1:2181", "--target=local", "-b")
            .withVolumeMounts(VolumeUtils.buildVolumeMount("volume-burry", "/home/burry"))
            .withImagePullPolicy("Always")
            .endContainer()

            .addNewVolume()
            .withName("volume-burry")
            .withNewPersistentVolumeClaim()
            .withClaimName("example-msch")
            .endPersistentVolumeClaim()
            .endVolume()

            .addNewVolume()
            .withName("burry")
            .withNewSecret()
            .withSecretName("example-msch")
            .endSecret()
            .endVolume()

            .addNewVolume()
            .withName("cluster-ca")
            .withNewSecret()
            .withSecretName("msch-cluster-ca-cert")
            .endSecret()
            .endVolume()

            .withRestartPolicy("OnFailure")
            .endSpec()

            .endTemplate()
            .endSpec()
            .endJobTemplate()
            .endSpec();

        return cronJobBuilder.build();
    }
}
