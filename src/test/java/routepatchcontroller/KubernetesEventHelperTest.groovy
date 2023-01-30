package routepatchcontroller

import io.fabric8.kubernetes.api.model.ObjectReference
import io.fabric8.openshift.api.model.RouteBuilder
import io.fabric8.openshift.client.OpenShiftClient
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer
import spock.lang.Specification

class KubernetesEventHelperTest extends Specification {


    OpenShiftMockServer openShiftMockServer
    OpenShiftClient openShiftClient

    void setup() {
        openShiftMockServer = new OpenShiftMockServer()
        openShiftClient = openShiftMockServer.createOpenShiftClient()
    }

    def "check we can create an event"() {
        given:
        String name = "myinstance"
        String namespace = "mynamespace"
        ObjectReference objectRegarding = KubernetesEventHelper.referenceForObj(new RouteBuilder().withNewMetadata().withName(name).withNamespace(namespace).withResourceVersion("v1").endMetadata().build())

        when:
        KubernetesEventHelper.createEvent(openShiftClient, name, namespace, objectRegarding, "hello")
        def req = openShiftMockServer.lastRequest

        then:
        req.path == "/api/requests/"
    }

}
