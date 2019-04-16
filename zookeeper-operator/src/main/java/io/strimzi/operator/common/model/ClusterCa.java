/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.model;

import io.fabric8.kubernetes.api.model.Secret;
import io.strimzi.api.kafka.model.CertificateExpirationPolicy;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.cluster.model.Ca;


/**
 * Based on ClusterCA of cluster-operator
 */
public class ClusterCa extends Ca {

    private final String clusterName;

    public ClusterCa(CertManager certManager, String clusterName, Secret caCertSecret, Secret caKeySecret) {
        this(certManager, clusterName, caCertSecret, caKeySecret, 365, 30, true, null);
    }

    public ClusterCa(CertManager certManager,
                     String clusterName,
                     Secret clusterCaCert,
                     Secret clusterCaKey,
                     int validityDays,
                     int renewalDays,
                     boolean generateCa,
                     CertificateExpirationPolicy policy) {
        super(certManager, "cluster-ca",
            KafkaResources.clusterCaCertificateSecretName(clusterName),
            forceRenewal(clusterCaCert, clusterCaKey, "cluster-ca.key"),
            KafkaResources.clusterCaKeySecretName(clusterName),
            adapt060ClusterCaSecret(clusterCaKey),
            validityDays, renewalDays, generateCa, policy);
        this.clusterName = clusterName;
    }

    /**
     * In Strimzi 0.6.0 the Secrets and keys used a different convention.
     * Here we adapt the keys in the {@code *-cluster-ca} Secret to match what
     * 0.7.0 expects.
     *
     * @param clusterCaKey Secret cluster Ca
     * @return cluster ca
     */
    public static Secret adapt060ClusterCaSecret(Secret clusterCaKey) {
        if (clusterCaKey != null && clusterCaKey.getData() != null) {
            String key = clusterCaKey.getData().get("cluster-ca.key");
            if (key != null) {
                clusterCaKey.getData().put("ca.key", key);
            }
        }
        return clusterCaKey;
    }


    @Override
    public String toString() {
        return "cluster-ca";
    }

}
