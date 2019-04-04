/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.zookeeper.model;

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
import io.strimzi.certs.CertManager;
import io.strimzi.operator.common.model.BatchModel;
import io.strimzi.operator.common.model.ExtensionsModel;
import io.strimzi.operator.common.model.ImagePullPolicy;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.operator.common.model.StandardModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractZookeeperModel<T extends CustomResource> implements StandardModel<T>, BatchModel<T>, ExtensionsModel<T> {

    protected final String namespace;
    protected final String name;
    protected final Labels labels;
    protected final String clusterName;
    protected final ImagePullPolicy imagePullPolicy;
    protected NetworkPolicy networkPolicy;

    /**
     * Constructor
     *
     * @param namespace Kubernetes/OpenShift namespace where cluster resources are going to be created
     * @param name      Zookeeper Backup name
     * @param labels    Labels
     */
    public AbstractZookeeperModel(String namespace, String name, Labels labels, ImagePullPolicy imagePullPolicy) {
        this.namespace = namespace;
        this.name = name;
        this.labels = labels.withName(name);
        this.clusterName = clusterName();
        this.imagePullPolicy = imagePullPolicy;
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
    public CronJob getCronJob() {
        return null;
    }


    @Override
    public Job getJob() {
        return null;
    }


    @Override
    public PersistentVolumeClaim getStorage() {
        return null;
    }

    @Override
    public Secret getSecret() {
        return null;
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
            //.withOwnerReferences(createOwnerReference(customResource))
            .endMetadata()
            .withNewSpec()
            .withPodSelector(podSelector)
            .withIngress(rules)
            .endSpec()
            .build();

    }

    @Override
    public NetworkPolicy getNetworkPolicy() {
        return networkPolicy;
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
