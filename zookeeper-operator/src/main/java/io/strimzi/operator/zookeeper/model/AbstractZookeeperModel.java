/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPort;
import io.fabric8.kubernetes.client.CustomResource;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.Storage;
import io.strimzi.certs.CertManager;
import io.strimzi.operator.burry.api.Burryfest;
import io.strimzi.operator.burry.api.BurryfestBuilder;
import io.strimzi.operator.common.exception.InvalidResourceException;
import io.strimzi.operator.common.model.BatchModel;
import io.strimzi.operator.common.model.ExtensionsModel;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.StandardModel;
import io.strimzi.operator.common.operator.resource.SecretOperator;
import io.strimzi.operator.common.utils.SecretUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.strimzi.operator.burry.model.AbstractBurryModel.BURRYFEST_FILENAME;

public abstract class AbstractZookeeperModel<T extends CustomResource> implements StandardModel<T>, BatchModel<T>, ExtensionsModel<T> {

    protected final String namespace;
    protected final String name;
    protected final Labels labels;
    protected final String clusterName;
    protected final ImagePullPolicy imagePullPolicy;
    protected final SecretOperator secretOperator;

    protected NetworkPolicy networkPolicy;
    protected Secret secret;
    protected Secret burry;
    protected CronJob cronJob;
    protected Job job;

    /**
     * Constructor
     *
     * @param namespace       Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name            Zookeeper Backup name
     * @param labels          Labels
     * @param imagePullPolicy Image Pull Policy
     * @param secretOperator  SecretOperator to mange secret resources
     */
    public AbstractZookeeperModel(String namespace, String name, Labels labels, ImagePullPolicy imagePullPolicy, SecretOperator secretOperator) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels.withName(name);
        this.clusterName = clusterName();
        this.imagePullPolicy = imagePullPolicy;
        this.secretOperator = secretOperator;
    }

    /**
     * Return the storage type
     *
     * @param customResource desired resource
     * @return Storage type
     */
    protected abstract String getBurryStorageType(T customResource);


    /**
     * @return secret config
     */
    public Secret getConfig() {
        return this.burry;
    }

    /**
     * @param customResource desired resource
     */
    public void addConfig(T customResource) {
        final String type = getBurryStorageType(customResource);
        if (Storage.TYPE_S3.equalsIgnoreCase(type)) {
            try {
                String secretName = ZookeeperOperatorResources.burrySecretManifestName(clusterName, type);
                this.burry = this.secretOperator.get(namespace, secretName); //must pre exists
                if (burry == null) {
                    throw new InvalidResourceException("The secret " + secretName + " does not exist on namespace" + namespace);
                }
                final String key = burry.getData().get(BURRYFEST_FILENAME);
                if (key == null || key.isEmpty()) {
                    throw new InvalidResourceException("The secret " + secretName + " does not contain the key " + BURRYFEST_FILENAME + "  on namespace" + namespace);
                }
                // validate .burryfest content
                new ObjectMapper().readValue(SecretUtils.decodeFromSecret(this.burry, BURRYFEST_FILENAME), Burryfest.class);
            } catch (IOException e) {
                throw new InvalidResourceException("Invalid " + BURRYFEST_FILENAME + " content: " + e.getMessage() + ". on namespace" + namespace);
            }
        } else if (Storage.TYPE_PERSISTENT_CLAIM.equalsIgnoreCase(type)) {
            createBurryfestSecret(type);
        }
    }

    /**
     * Create Burryfest Secret
     *
     * @param type storage type
     * @return secretName
     */
    protected String createBurryfestSecret(String type) {
        String secretName = ZookeeperOperatorResources.burrySecretManifestName(clusterName, type);
        Burryfest burryfest = new BurryfestBuilder()
            .withTarget("local")
            .withSvc("zk")
            .withTimeout(1)
            .withSvcEndpoint("127.0.0.1:2181").build();
        Map<String, String> data = new HashMap<>(1);
        data.put(BURRYFEST_FILENAME, SecretUtils.encodeValue(burryfest.toString()));
        this.burry = SecretUtils.createSecret(secretName, namespace, labels, null, data);
        return secretName;
    }

    /**
     * Return cluster name
     *
     * @return String
     */
    private String clusterName() {
        return labels.toMap().get(Labels.STRIMZI_CLUSTER_LABEL);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Labels getLabels() {
        return labels;
    }


    @Override
    public Secret getSecret() {
        return secret;
    }

    @Override
    public CronJob getCronJob() {
        return cronJob;
    }

    @Override
    public Job getJob() {
        return job;
    }


    @Override
    public PersistentVolumeClaim getStorage() {
        return null;
    }


    @Override
    public NetworkPolicy getNetworkPolicy() {
        return networkPolicy;
    }


    @Override
    public void addCronJob(T customResource) {

    }

    @Override
    public void addJob(T customResource) {

    }

    @Override
    public void addSecret(CertManager certManager, Secret clusterCaCert, Secret clusterCaKey, Secret certSecret) {

    }

    @Override
    public void addStorage(T customResource) {

    }


    @Override
    public void addNetworkPolicy(T customResource) {

        List<NetworkPolicyIngressRule> rules = new ArrayList<>(1);

        NetworkPolicyPort port1 = new NetworkPolicyPort();
        port1.setPort(new IntOrString(2181));

        //from
        NetworkPolicyPeer zookeeperClusterPeer = new NetworkPolicyPeer();
        LabelSelector labelSelector2 = new LabelSelector();
        Map<String, String> expressions2 = new HashMap<>();
        expressions2.put(Labels.STRIMZI_KIND_LABEL, customResource.getKind());
        labelSelector2.setMatchLabels(expressions2);
        zookeeperClusterPeer.setPodSelector(labelSelector2);

        NetworkPolicyIngressRule networkPolicyIngressRule = new NetworkPolicyIngressRuleBuilder()
            .withPorts(port1)
            .withFrom(zookeeperClusterPeer)
            .build();

        rules.add(networkPolicyIngressRule);


        //podSelector
        LabelSelector podSelector = new LabelSelector();
        Map<String, String> expressions = new HashMap<>();
        expressions.put(Labels.STRIMZI_NAME_LABEL, KafkaResources.zookeeperStatefulSetName(clusterName));
        podSelector.setMatchLabels(expressions);

        networkPolicy = new NetworkPolicyBuilder()
            .withNewMetadata()
            .withName(ZookeeperOperatorResources.networkPolicyName(clusterName, customResource.getKind().toLowerCase(Locale.getDefault())))
            .withNamespace(namespace)
            .withLabels(labels.toMap())
            //.withOwnerReferences(createOwnerReference(customResource)) it has to be independent in case of delete crd it will drop the network policy
            .endMetadata()
            .withNewSpec()
            .withPodSelector(podSelector)
            .withIngress(rules)
            .endSpec()
            .build();

    }

    protected OwnerReference createOwnerReference(HasMetadata metadata) {
        return new OwnerReferenceBuilder()
            .withApiVersion(metadata.getApiVersion())
            .withKind(metadata.getKind())
            .withName(clusterName)
            .withUid(metadata.getMetadata().getUid())
            .withBlockOwnerDeletion(false)
            .withController(false)
            .build();
    }
}
