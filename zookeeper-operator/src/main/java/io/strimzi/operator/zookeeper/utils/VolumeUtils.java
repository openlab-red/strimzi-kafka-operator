/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.zookeeper.utils;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.operator.common.model.Labels;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class VolumeUtils {

    private VolumeUtils() {}

    private static final Logger log = LogManager.getLogger(VolumeUtils.class.getName());


    /**
     * Build Persistent Volume Claim
     *
     * @param storage Storage with the storage configuration
     * @return
     */
    public static PersistentVolumeClaim buildPersistentVolumeClaim(String name, String namespace, Labels labels, PersistentClaimStorage storage) {
        Map<String, Quantity> requests = new HashMap<>();
        requests.put("storage", new Quantity(storage.getSize(), null));
        LabelSelector selector = null;
        if (storage.getSelector() != null && !storage.getSelector().isEmpty()) {
            selector = new LabelSelector(null, storage.getSelector());
        }

        PersistentVolumeClaimBuilder pvcb = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(name)
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


    public static VolumeMount buildVolumeMount(String name, String path) {
        VolumeMount volumeMount = new VolumeMountBuilder()
            .withName(name)
            .withMountPath(path)
            .build();
        log.trace("Created volume mount {}", volumeMount);
        return volumeMount;
    }

}
