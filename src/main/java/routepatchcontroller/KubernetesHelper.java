package routepatchcontroller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import java.util.Optional;

public class KubernetesHelper {
    public static Optional<String> labelValue(HasMetadata resource, String label) {
        return Optional.ofNullable(resource.getMetadata().getLabels())
                .flatMap(labels -> Optional.ofNullable(labels.get(label)));
    }

    public static void createEvent(OpenShiftClient client, String instanceName, String namespace, ObjectReference regarding, String note) {
        // emit event for the route
        var event = new EventBuilder().withNewMetadata()
                .withGenerateName("route-patch-controller")
                .withNamespace(namespace).endMetadata()
                .withRegarding(regarding)
                .withReportingController("route-patch-controller")
                .withReportingInstance(instanceName)
                .withType("Normal")
                .withReason("TODO")
                .withAction("TODO")
                .withEventTime(new MicroTime())
                .withNote(note)
                .build();
        client.events().v1().events().inNamespace(namespace).resource(event).create();
    }
}
