package routepatchcontroller;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class KubernetesHelper {
    private static final DateTimeFormatter k8sMicroTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.'SSSSSSXXX");
    public static void createEvent(OpenShiftClient client, String instanceName, String namespace, ObjectReference regarding, String reason, String action, String note) {
        // emit event for the route
        var event = new EventBuilder().withNewMetadata()
                    .withGenerateName("route-patch-controller")
                    .withNamespace(namespace)
                .endMetadata()
                .withRegarding(regarding)
                .withReportingController("route-patch-controller")
                .withReportingInstance(instanceName)
                .withType("Normal")
                .withReason(reason)
                .withAction(action)
                .withEventTime(new MicroTimeBuilder().withTime(k8sMicroTime.format(ZonedDateTime.now())).build())
                .withNote(note)
                .build();
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

    public static LabelSelector buildLabelSelectorFromString(String labelSelector) {
        var labels = decodeQueryString(labelSelector);
        return new LabelSelectorBuilder().addToMatchLabels(labels).build();
    }

    private static Map<String, String> decodeQueryString(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
            if (!key.isEmpty()) {
                params.put(key, value);
            }
        }
        return params;
    }

    public static boolean evalLabelSelectorOn(LabelSelector selector, HasMetadata resource) {
        if (selector == null) // no seletor, everything matches
            return true;
        final var labels = resource.getMetadata().getLabels(); // there is a selector, but no labels, nothing matches
        if (labels == null)
            return false;
        final var matchLabels = selector.getMatchLabels();
        if (matchLabels != null) {
            for (final var entry : matchLabels.entrySet()) {
                final var labelValue = labels.get(entry.getKey());
                if (!entry.getValue().equals(labelValue))
                    return false;
            }
        }
        final var matchExpressions = selector.getMatchExpressions();
        for (final var req : matchExpressions) {
            final var operator = req.getOperator();
            final var key = req.getKey();
            final var matches = switch (operator) {
                case "In" -> labels.containsKey(key) && req.getValues().stream().anyMatch(x -> x.equals(labels.get(key)));
                case "NotIn" -> labels.containsKey(key) && req.getValues().stream().noneMatch(x -> x.equals(labels.get(key)));
                case "DoesNotExist" -> !labels.containsKey(key);
                case "Exists" -> labels.containsKey(key);
                default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
            };
            if (!matches)
                return false;
        }
        return true;
    }

    public static Optional<String> labelValue(HasMetadata resource, String labelName) {
        final var labels = resource.getMetadata().getLabels();
        return labels != null ? Optional.ofNullable(labels.get(labelName)) : Optional.empty();
    }

}
