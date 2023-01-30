package routepatchcontroller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

public class KubernetesEventHelper {
    public static void createEvent(OpenShiftClient client, String instanceName, String namespace, ObjectReference regarding, String note) {
        // emit event for the route
        var event = new EventBuilder().withNewMetadata()
                    .withGenerateName("route-patch-controller")
                    .withNamespace(namespace)
                .endMetadata()
                .withRegarding(regarding)
                .withReportingController("route-patch-controller")
                .withReportingInstance(instanceName)
                .withType("Normal")
                .withReason("TODO")
                .withAction("TODO")
                .withEventTime(new MicroTime())
                .withNote(note)
                .build();
        System.out.println("event " + event);
        client.events().v1().events().inNamespace(namespace).resource(event).create();
    }

    public static ObjectReference referenceForObj(HasMetadata route) {
        return new ObjectReferenceBuilder()
                .withKind(route.getKind())
                .withApiVersion(route.getApiVersion())
                .withName(route.getMetadata().getName())
                .withNamespace(route.getMetadata().getNamespace())
                .withUid(route.getMetadata().getUid())
                .withResourceVersion(route.getMetadata().getResourceVersion())
                .build();
    }
}
