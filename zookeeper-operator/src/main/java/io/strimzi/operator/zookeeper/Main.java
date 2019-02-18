/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.ZookeeperBackupList;
import io.strimzi.api.kafka.ZookeeperRestoreList;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.api.kafka.model.DoneableZookeeperBackup;
import io.strimzi.api.kafka.model.DoneableZookeeperRestore;
import io.strimzi.certs.OpenSslCertManager;
import io.strimzi.operator.common.operator.resource.CrdOperator;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.zookeeper.operator.backup.ZookeeperBackupOperator;
import io.strimzi.operator.zookeeper.operator.restore.ZookeeperRestoreOperator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
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

        CrdOperator<KubernetesClient, ZookeeperBackup, ZookeeperBackupList, DoneableZookeeperBackup> crdZookeeperBackupOperations = new CrdOperator<>(vertx, client, ZookeeperBackup.class, ZookeeperBackupList.class, DoneableZookeeperBackup.class);
        ZookeeperBackupOperator zookeeperBackupOperations = new ZookeeperBackupOperator(vertx,
            certManager, crdZookeeperBackupOperations, secretOperations, config.getCaCertSecretName(), config.getCaKeySecretName(), config.getCaNamespace());


        CrdOperator<KubernetesClient, ZookeeperRestore, ZookeeperRestoreList, DoneableZookeeperRestore> crdZookeeperRestoreOperations = new CrdOperator<>(vertx, client, ZookeeperRestore.class, ZookeeperRestoreList.class, DoneableZookeeperRestore.class);
        ZookeeperRestoreOperator zookeeperRestoreOperations = new ZookeeperRestoreOperator(vertx,
            certManager, crdZookeeperRestoreOperations, secretOperations, config.getCaCertSecretName(), config.getCaKeySecretName(), config.getCaNamespace());


        Future<String> fut = Future.future();
        ZookeeperOperator operator = new ZookeeperOperator(config.getNamespace(),
            config,
            client,
            zookeeperBackupOperations,
            zookeeperRestoreOperations);
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
