/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.strimzi.operator.common.operator.Operator;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An "operator" for managing assemblies of various types <em>in a particular namespace</em>.
 */
public class ZookeeperVerticle extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(ZookeeperVerticle.class.getName());

    private final KubernetesClient client;
    private final String namespace;
    private final long reconciliationInterval;
    private final Operator<? extends CustomResource> operator;

    private Watch watch;
    private long reconcileTimer;

    public ZookeeperVerticle(String namespace,
                             ZookeeperOperatorConfig config,
                             KubernetesClient client,
                             Operator<? extends CustomResource> operator) {
        log.info("Creating ZookeeperOperator for namespace {}", namespace);
        this.namespace = namespace;
        this.reconciliationInterval = config.getReconciliationIntervalMs();
        this.client = client;
        this.operator = operator;
    }

    Consumer<KubernetesClientException> recreateWatch(Operator<? extends CustomResource> op) {
        Consumer<KubernetesClientException> cons = new Consumer<KubernetesClientException>() {
            @Override
            public void accept(KubernetesClientException e) {
                if (e != null) {
                    log.error("Watcher closed with exception in namespace {}", namespace, e);
                    op.createWatch(namespace, this);
                } else {
                    log.info("Watcher closed in namespace {}", namespace);
                }
            }
        };
        return cons;
    }

    @Override
    public void start(Future<Void> start) {
        log.info("Starting ZookeeperOperator for namespace {}", namespace);

        // Configure the executor here, but it is used only in other places
        getVertx().createSharedWorkerExecutor("kubernetes-ops-pool", 10, TimeUnit.SECONDS.toNanos(120));

        operator.createWatch(namespace, recreateWatch(operator))
            .compose(w -> {
                final String simpleName = operator.getClass().getSimpleName();
                log.info("Started operator for {} kind", simpleName);
                watch = w;
                log.info("Setting up periodical reconciliation for namespace {}", namespace);
                this.reconcileTimer = vertx.setPeriodic(this.reconciliationInterval, res2 -> {
                    log.info("Triggering periodic reconciliation for namespace {}...", namespace);
                    operator.reconcileAll(simpleName + "-timer", namespace);
                });
                return startHealthServer(operator.getPort()).map((Void) null);
            }).compose(start::complete, start);
    }

    @Override
    public void stop(Future<Void> stop) {
        log.info("Stopping ZookeeperOperator for namespace {}", namespace);
        vertx.cancelTimer(reconcileTimer);

        if (watch != null) {
            watch.close();
        }

        client.close();
        stop.complete();
    }

    /**
     * Start an HTTP health server
     */
    private Future<HttpServer> startHealthServer(int port) {
        Future<HttpServer> result = Future.future();
        this.vertx.createHttpServer()
            .requestHandler(request -> {
                if (request.path().equals("/healthy")) {
                    request.response().setStatusCode(200).end();
                } else if (request.path().equals("/ready")) {
                    request.response().setStatusCode(200).end();
                }
            })
            .listen(port, ar -> {
                if (ar.succeeded()) {
                    log.info("ZookeeperOperator is now ready (health server listening on {})", port);
                } else {
                    log.error("Unable to bind health server on {}", port, ar.cause());
                }
                result.handle(ar);
            });
        return result;
    }

}
