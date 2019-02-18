/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import io.fabric8.kubernetes.api.model.Secret;
import io.strimzi.api.kafka.model.ZookeeperRestore;
import io.strimzi.certs.CertAndKey;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.model.Labels;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;

public class ZookeeperRestoreModel {
    private static final Logger log = LogManager.getLogger(ZookeeperRestoreModel.class.getName());

    protected final String namespace;
    protected final String name;
    protected final Labels labels;

    protected String caCert;
    protected CertAndKey zkRestoreCertAndKey;

    public static final String ENV_VAR_CLIENTS_CA_VALIDITY = "STRIMZI_CA_VALIDITY";
    public static final String ENV_VAR_CLIENTS_CA_RENEWAL = "STRIMZI_CA_RENEWAL";

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where Kafka Connect cluster resources are going to be created
     * @param name      Zookeeper Restore name
     * @param labels    Labels
     */
    protected ZookeeperRestoreModel(String namespace, String name, Labels labels) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels;
    }

    /**
     * Creates instance of ZookeeperRestoreModel from CRD definition
     *
     * @param certManager CertManager instance for work with certificates
     * @return
     */
    public static ZookeeperRestoreModel fromCrd(CertManager certManager,
                                                ZookeeperRestore zookeeperRestore,
                                                Secret clientsCaCert,
                                                Secret clientsCaKey) {
        ZookeeperRestoreModel result = new ZookeeperRestoreModel(zookeeperRestore.getMetadata().getNamespace(),
            zookeeperRestore.getMetadata().getName(),
            Labels.fromResource(zookeeperRestore).withKind(zookeeperRestore.getKind()));


        return result;
    }


    /**
     * Decode from Base64 a keyed value from a Secret
     *
     * @param secret Secret from which decoding the value
     * @param key    Key of the value to decode
     * @return decoded value
     */
    protected byte[] decodeFromSecret(Secret secret, String key) {
        return Base64.getDecoder().decode(secret.getData().get(key));
    }


}
