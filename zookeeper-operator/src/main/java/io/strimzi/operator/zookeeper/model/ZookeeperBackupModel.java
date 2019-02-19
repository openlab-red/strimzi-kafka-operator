/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;


import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.api.kafka.model.Storage;
import io.strimzi.api.kafka.model.ZookeeperBackup;
import io.strimzi.api.kafka.model.ZookeeperBackupSpec;
import io.strimzi.api.kafka.model.CertificateAuthority;
import io.strimzi.certs.CertAndKey;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.cluster.model.ClusterCa;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.zookeeper.ZookeeperOperatorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ZookeeperBackupModel {
    private static final Logger log = LogManager.getLogger(ZookeeperBackupModel.class.getName());

    protected final String namespace;
    protected final String name;
    protected final Labels labels;
    protected Storage storage;

    protected String caCert;
    protected CertAndKey zkBackupCertAndKey;

    public static final String ENV_VAR_CLIENTS_CA_VALIDITY = "STRIMZI_CA_VALIDITY";
    public static final String ENV_VAR_CLIENTS_CA_RENEWAL = "STRIMZI_CA_RENEWAL";

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where Kafka Connect cluster resources are going to be created
     * @param name      Zookeeper Backup name
     * @param labels    Labels
     */
    protected ZookeeperBackupModel(String namespace, String name, Labels labels) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels;
    }

    /**
     * Creates instance of ZookeeperBackupModel from CRD definition
     *
     * @param certManager CertManager instance for work with certificates
     * @return
     */
    public static ZookeeperBackupModel fromCrd(CertManager certManager,
                                               ZookeeperBackup zookeeperBackup,
                                               Secret clusterCaCert,
                                               Secret clusterCaKey,
                                               Secret backupSecret) {
        ZookeeperBackupModel result = new ZookeeperBackupModel(zookeeperBackup.getMetadata().getNamespace(),
            zookeeperBackup.getMetadata().getName(),
            Labels.fromResource(zookeeperBackup).withKind(zookeeperBackup.getKind()));
        ZookeeperBackupSpec zookeeperBackupSpec = zookeeperBackup.getSpec();

        if (zookeeperBackupSpec.getStorage() instanceof PersistentClaimStorage) {
            PersistentClaimStorage persistentClaimStorage = (PersistentClaimStorage) zookeeperBackupSpec.getStorage();
            if (persistentClaimStorage.getSize() == null || persistentClaimStorage.getSize().isEmpty()) {
                throw new InvalidResourceException("The size is mandatory for a persistent-claim storage");
            }
        }
        result.setStorage(zookeeperBackupSpec.getStorage());


        result.generateCertificates(certManager, clusterCaCert, clusterCaKey, backupSecret,
            ZookeeperBackupModel.getClusterCaValidityDays(), ZookeeperBackupModel.getClusterCaRenewalDays());

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

    /**
     * Generates the name of the ZookeeperBackup based on the backup name
     *
     * @return
     */
    public static String getName(String name) {
        return name;
    }

    /**
     * Generates secret containing the certificate for TLS client auth when TLS client auth is enabled for the backup.
     * Returns null otherwise.
     *
     * @return
     */
    public Secret generateSecret() {
        Map<String, String> data = new HashMap<>();
        data.put("ca.crt", caCert);
        data.put("backup.key", zkBackupCertAndKey.keyAsBase64String());
        data.put("backup.crt", zkBackupCertAndKey.certAsBase64String());
        return createSecret(data);

    }

    /**
     * Creates secret with the data
     *
     * @param data Map with the Secret content
     * @return
     */
    protected Secret createSecret(Map<String, String> data) {
        Secret s = new SecretBuilder()
            .withNewMetadata()
            .withName(getName())
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withData(data)
            .build();

        return s;
    }


    public PersistentVolumeClaim generatePersistentVolumeClaim() {
        if (storage instanceof PersistentClaimStorage) {
            return createPersistentVolumeClaim(getName(), (PersistentClaimStorage) getStorage());
        }
        return null;
    }

    /**
     * Creates Persistent Volume Claim
     *
     * @param storage Storage with the storage configuration
     * @return
     */
    protected PersistentVolumeClaim createPersistentVolumeClaim(String name, PersistentClaimStorage storage) {
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("storage", new Quantity(storage.getSize(), null));
        LabelSelector selector = null;
        if (storage.getSelector() != null && !storage.getSelector().isEmpty()) {
            selector = new LabelSelector(null, storage.getSelector());
        }

        PersistentVolumeClaimBuilder pvcb = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(getName())
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .withRequests(requests)
            .endResources()
            .withStorageClassName(storage.getStorageClass())
            .withSelector(selector)
            .endSpec();

        return pvcb.build();
    }

    public String getName() {
        return ZookeeperBackupModel.getName(name);
    }

    /**
     * Manage certificates generation based on those already present in the Secrets
     *
     * @param certManager  CertManager instance for handling certificates creation
     * @param backupSecret Secret with the user certificate
     */
    public void generateCertificates(CertManager certManager,
                                     Secret clusterCaCertSecret, Secret clusterCaKeySecret,
                                     Secret backupSecret, int validityDays, int renewalDays) {
        if (clusterCaCertSecret == null) {
            throw new NoCertificateSecretException("The Cluster CA Cert Secret is missing");
        } else if (clusterCaKeySecret == null) {
            throw new NoCertificateSecretException("The Cluster CA Key Secret is missing");
        } else {
            ClusterCa clusterCa = new ClusterCa(certManager,
                clusterCaCertSecret.getMetadata().getName(),
                clusterCaCertSecret,
                clusterCaKeySecret,
                validityDays,
                renewalDays,
                false,
                null);
            this.caCert = clusterCa.currentCaCertBase64();
            if (backupSecret != null) {
                // Secret already exists -> lets verify if it has keys from the same CA
                String originalCaCrt = clusterCaCertSecret.getData().get("ca.crt");
                String caCrt = backupSecret.getData().get("ca.crt");
                String userCrt = backupSecret.getData().get("backup.crt");
                String userKey = backupSecret.getData().get("backup.key");
                if (originalCaCrt != null
                    && originalCaCrt.equals(caCrt)
                    && userCrt != null
                    && !userCrt.isEmpty()
                    && userKey != null
                    && !userKey.isEmpty()) {
                    this.zkBackupCertAndKey = new CertAndKey(
                        decodeFromSecret(backupSecret, "backup.key"),
                        decodeFromSecret(backupSecret, "backup.crt"));
                    return;
                }
            }

            try {
                this.zkBackupCertAndKey = clusterCa.generateSignedCert(name);
            } catch (IOException e) {
                log.error("Error generating signed certificate for user {}", name, e);
            }

        }
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

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

}
