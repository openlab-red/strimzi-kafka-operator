/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper;

import io.strimzi.api.kafka.model.CertificateAuthority;
import io.strimzi.operator.common.InvalidConfigurationException;
import io.strimzi.operator.common.model.Labels;

import java.util.Map;

/**
 * Cluster Operator configuration
 */
public class ZookeeperOperatorConfig {

    public static final String STRIMZI_NAMESPACE = "STRIMZI_NAMESPACE";
    public static final String STRIMZI_FULL_RECONCILIATION_INTERVAL_MS = "STRIMZI_FULL_RECONCILIATION_INTERVAL_MS";
    public static final String STRIMZI_LABELS = "STRIMZI_LABELS";
    public static final String STRIMZI_CA_CERT_SECRET_NAME = "STRIMZI_CA_CERT_NAME";
    public static final String STRIMZI_CA_KEY_SECRET_NAME = "STRIMZI_CA_KEY_NAME";
    public static final String STRIMZI_CA_NAMESPACE = "STRIMZI_CA_NAMESPACE";
    public static final String STRIMZI_CLUSTER_CA_VALIDITY = "STRIMZI_CA_VALIDITY";
    public static final String STRIMZI_CLUSTER_CA_RENEWAL = "STRIMZI_CA_RENEWAL";

    public static final String STRIMZI_ZOOKEEPER_OPERATOR_BURRY_IMAGE =
        System.getenv().getOrDefault("STRIMZI_DEFAULT_ZOOKEEPER_OPERATOR_BURRY_IMAGE",
            "openlabred/burry:v1.0.2");
    public static final String STRIMZI_ZOOKEEPER_OPERATOR_TLS_SIDECAR_BURRY_IMAGE =
        System.getenv().getOrDefault("STRIMZI_DEFAULT_ZOOKEEPER_OPERATOR_TLS_SIDECAR_BURRY_IMAGE",
            "openlabred/burry-stunnel:v1.0.0");

    public static final long DEFAULT_FULL_RECONCILIATION_INTERVAL_MS = 120_000;
    public static final String ZOOKEEPER_BACKUP_CERT_NAME = "backup";

    private final String namespace;
    private final long reconciliationIntervalMs;
    private Labels labels;
    private final String caCertSecretName;
    private final String caKeySecretName;
    private final String caNamespace;

    /**
     * Constructor
     *
     * @param namespace                 namespace in which the operator will run and create resources
     * @param reconciliationIntervalMs  specify every how many milliseconds the reconciliation runs
     * @param labels                    Map with labels which should be used to find the ZookeeperOperator resources
     * @param caCertSecretName          Name of the secret containing the Certification Authority
     * @param caNamespace               Namespace with the CA secret
     */
    public ZookeeperOperatorConfig(String namespace,
                                   long reconciliationIntervalMs,
                                   Labels labels, String caCertSecretName,
                                   String caKeySecretName,
                                   String caNamespace) {
        this.namespace = namespace;
        this.reconciliationIntervalMs = reconciliationIntervalMs;
        this.labels = labels;
        this.caCertSecretName = caCertSecretName;
        this.caKeySecretName = caKeySecretName;
        this.caNamespace = caNamespace;
    }

    /**
     * Loads configuration parameters from a related map
     *
     * @param map map from which loading configuration parameters
     * @return Zookeeper Operator configuration instance
     */
    public static ZookeeperOperatorConfig fromMap(Map<String, String> map) {

        String namespace = map.get(ZookeeperOperatorConfig.STRIMZI_NAMESPACE);
        if (namespace == null || namespace.isEmpty()) {
            throw new InvalidConfigurationException(ZookeeperOperatorConfig.STRIMZI_NAMESPACE + " cannot be null");
        }

        long reconciliationInterval = DEFAULT_FULL_RECONCILIATION_INTERVAL_MS;
        String reconciliationIntervalEnvVar = map.get(ZookeeperOperatorConfig.STRIMZI_FULL_RECONCILIATION_INTERVAL_MS);
        if (reconciliationIntervalEnvVar != null) {
            reconciliationInterval = Long.parseLong(reconciliationIntervalEnvVar);
        }

        Labels labels;
        try {
            labels = Labels.fromString(map.get(STRIMZI_LABELS));
        } catch (Exception e) {
            throw new InvalidConfigurationException("Failed to parse labels from " + STRIMZI_LABELS, e);
        }

        String caCertSecretName = map.get(ZookeeperOperatorConfig.STRIMZI_CA_CERT_SECRET_NAME);
        if (caCertSecretName == null || caCertSecretName.isEmpty()) {
            throw new InvalidConfigurationException(ZookeeperOperatorConfig.STRIMZI_CA_CERT_SECRET_NAME + " cannot be null");
        }

        String caKeySecretName = map.get(ZookeeperOperatorConfig.STRIMZI_CA_KEY_SECRET_NAME);
        if (caKeySecretName == null || caKeySecretName.isEmpty()) {
            throw new InvalidConfigurationException(ZookeeperOperatorConfig.STRIMZI_CA_KEY_SECRET_NAME + " cannot be null");
        }

        String caNamespace = map.get(ZookeeperOperatorConfig.STRIMZI_CA_NAMESPACE);
        if (caNamespace == null || caNamespace.isEmpty()) {
            caNamespace = namespace;
        }

        return new ZookeeperOperatorConfig(namespace, reconciliationInterval, labels, caCertSecretName, caKeySecretName, caNamespace);
    }

    public static int getClusterCaValidityDays() {
        return getIntProperty(ZookeeperOperatorConfig.STRIMZI_CLUSTER_CA_VALIDITY, CertificateAuthority.DEFAULT_CERTS_VALIDITY_DAYS);
    }

    public static int getClusterCaRenewalDays() {
        return getIntProperty(ZookeeperOperatorConfig.STRIMZI_CLUSTER_CA_RENEWAL, CertificateAuthority.DEFAULT_CERTS_RENEWAL_DAYS);
    }

    private static int getIntProperty(String name, int defaultVal) {
        String env = System.getenv(name);
        if (env != null) {
            return Integer.parseInt(env);
        } else {
            return defaultVal;
        }
    }

    /**
     * @return namespace in which the operator runs and creates resources
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return how many milliseconds the reconciliation runs
     */
    public long getReconciliationIntervalMs() {
        return reconciliationIntervalMs;
    }

    /**
     * @return The labels which should be used as selecter
     */
    public Labels getLabels() {
        return labels;
    }

    /**
     * @return The name of the secret with the Client CA
     */
    public String getCaCertSecretName() {
        return caCertSecretName;
    }

    /**
     * @return The name of the secret with the Client CA
     */
    public String getCaKeySecretName() {
        return caKeySecretName;
    }

    /**
     * @return The namespace of the Client CA
     */
    public String getCaNamespace() {
        return caNamespace;
    }


    @Override
    public String toString() {
        return "ClusterOperatorConfig(" +
            "namespace=" + namespace +
            ",reconciliationIntervalMs=" + reconciliationIntervalMs +
            ",labels=" + labels +
            ",caName=" + caCertSecretName +
            ",caNamespace=" + caNamespace +
            ")";
    }
}
