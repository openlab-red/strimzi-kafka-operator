/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.ZookeeperRestoreList;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.DoneableZookeeperRestore;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.certs.OpenSslCertManager;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.CronJobOperator;
import io.strimzi.operator.common.operator.resource.JobOperator;
import io.strimzi.operator.common.operator.resource.PodOperator;
import io.strimzi.operator.common.operator.resource.PvcOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.operator.resource.SimpleStatefulSetOperator;
import io.strimzi.operator.zookeeper.operator.ZookeeperBackupOperator;
import io.strimzi.operator.zookeeper.operator.ZookeeperOperator;
import io.strimzi.operator.zookeeper.operator.ZookeeperRestoreOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class.getName());

    static {
        try {
            Crds.registerCustomKinds();
        } catch (Error | RuntimeException t) {
            log.error("Failed to register CRDs", t);
            throw t;
        }
    }

    public static void main(String[] args) {
        log.info("ZookeeperOperator {} is starting", Main.class.getPackage().getImplementationVersion());
        ZookeeperOperatorConfig config = ZookeeperOperatorConfig.fromMap(System.getenv());
        Vertx vertx = Vertx.vertx();
        KubernetesClient client = new DefaultKubernetesClient();
        run(vertx, client, config).setHandler(ar -> {
            if (ar.failed()) {
                log.error("Unable to start operator", ar.cause());
                System.exit(1);
            }
        });
    }

    static Future<String> run(Vertx vertx, KubernetesClient client, ZookeeperOperatorConfig config) {
        printEnvInfo();

        OpenSslCertManager certManager = new OpenSslCertManager();
        SecretOperator secretOperations = new SecretOperator(vertx, client);
        PvcOperator pvcOperator = new PvcOperator(vertx, client);
        CronJobOperator cronJobOperator = new CronJobOperator(vertx, client);
        JobOperator jobOperator = new JobOperator(vertx, client);
        PodOperator podOperations = new PodOperator(vertx, client);
        SimpleStatefulSetOperator simpleStatefulSetOperator = new SimpleStatefulSetOperator(vertx, client);


        CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdZookeeperBackupOperations = new CrdOperator<>(vertx, client, ZookeeperBackup.class, ZookeeperBackupList.class, DoneableZookeeperBackup.class);
        ZookeeperBackupOperator zookeeperBackupOperations = new ZookeeperBackupOperator(vertx,
            certManager, crdZookeeperBackupOperations, secretOperations, pvcOperator, cronJobOperator, podOperations, config.getCaCertSecretName(), config.getCaKeySecretName(), config.getCaNamespace());


        CrdOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore> crdZookeeperRestoreOperations = new CrdOperator<>(vertx, client, ZookeeperRestore.class, ZookeeperRestoreList.class, DoneableZookeeperRestore.class);
        ZookeeperRestoreOperator zookeeperRestoreOperations = new ZookeeperRestoreOperator(vertx,
            certManager, crdZookeeperRestoreOperations, secretOperations, pvcOperator, jobOperator, simpleStatefulSetOperator, config.getCaCertSecretName(), config.getCaKeySecretName(), config.getCaNamespace());

        List<ZookeeperOperator<? extends CustomResource>> operators = new ArrayList<>();
        operators.add(zookeeperBackupOperations);
        //operators.add(zookeeperRestoreOperations);

        ZookeeperVerticle operator = new ZookeeperVerticle(config.getNamespace(),
            config,
            client,
            operators);

        Future<String> fut = Future.future();
        vertx.deployVerticle(operator,
            res -> {
                if (res.succeeded()) {
                    log.info("Zookeeper Operator verticle started in namespace {}", config.getNamespace());
                } else {
                    log.error("Zookeeper Operator verticle in namespace {} failed to start", config.getNamespace(), res.cause());
                    System.exit(1);
                }
                fut.completer().handle(res);
            });

        return fut;
    }

    static void printEnvInfo() {
        Map<String, String> m = new HashMap<>(System.getenv());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : m.entrySet()) {
            sb.append("\t").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        log.info("Using config:\n" + sb.toString());
    }
}