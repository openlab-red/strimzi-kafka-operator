/*
 * Copyright 2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.common.utils;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.operator.common.model.Labels;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class VolumeUtils {

    private VolumeUtils() {
    }

    private static final Logger log = LogManager.getLogger(VolumeUtils.class.getName());


    /**
     * Build Persistent Volume Claim
     *
     * @param name      String name of the claim
     * @param namespace String namespace where to create the claim
     * @param labels    String associated labels
     * @param storage   Storage with the storage configuration
     * @return PersistentVolumeClaim
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


    /**
     * Build Volume Mount
     *
     * @param name name of the volume
     * @param path mount path
     * @return VolumeMount
     */
    public static VolumeMount buildVolumeMount(String name, String path, String... subPath) {
        VolumeMount volumeMount = new VolumeMountBuilder()
            .withName(name)
            .withMountPath(path)
            .withSubPath(subPath.length == 1 ? subPath[0] : null)
            .build();
        log.trace("Created volume mount {}", volumeMount);
        return volumeMount;
    }

    /**
     * Build Volume PVClaim type
     *
     * @param name      String name of the volume
     * @param claimName String pvc name
     * @return Volume
     */
    public static Volume buildVolumePVC(String name, String claimName) {
        Volume volume = new VolumeBuilder()
            .withName(name)
            .withNewPersistentVolumeClaim()
            .withClaimName(claimName)
            .endPersistentVolumeClaim().build();
        log.trace("Created volume claim {}", volume);
        return volume;
    }

    /**
     * Build Volume Secret type
     *
     * @param name       String name of the volume
     * @param secretName String secret name
     * @return Volume
     */
    public static Volume buildVolumeSecret(String name, String secretName) {
        Volume secret = new VolumeBuilder()
            .withName(name)
            .withNewSecret()
            .withSecretName(secretName)
            .endSecret().build();
        log.trace("Created volume secret {}", secret);
        return secret;
    }

}
