package routepatchcontroller;

import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class RoutePatcherService {

    private static final Logger logger = LoggerFactory.getLogger(RoutePatcherService.class);

    private final OpenShiftClient openShiftClient;
    private final ServiceConfiguration serviceConfiguration;

    public RoutePatcherService(OpenShiftClient openShiftClient, ServiceConfiguration serviceConfiguration) {
        this.openShiftClient = openShiftClient;
        this.serviceConfiguration = serviceConfiguration;
    }

    public void patchNamespace(Namespace namespace) {
        if (isControlledNamespace(serviceConfiguration, namespace)) {
            var routerName = namespaceRouter(serviceConfiguration, namespace);
            var routes = openShiftClient.routes().inNamespace(namespace.getMetadata().getName()).list().getItems();
            for (var route : routes)
                doPatchRoute(routerName, route);
        }
    }

    public void patchRoute(Route route) {
        var namespace = openShiftClient.namespaces().withName(route.getMetadata().getNamespace()).get();
        if (namespace == null) {
            logger.warn("{}: namespace {} is not found", route.getMetadata().getName(), route.getMetadata().getNamespace());
            return;
        }
        if (isControlledNamespace(serviceConfiguration, namespace)) {
            var routerName = namespaceRouter(serviceConfiguration, namespace);
            doPatchRoute(routerName, route);
        }
    }

    private void doPatchRoute(String routerName, Route route) {
        var name = route.getMetadata().getName();
        var host = route.getSpec().getHost();
        var routeRef = RoutePatcherService.referenceForRoute(route);

        var targetDomain = serviceConfiguration.routerDomains().get(routerName);
        if (targetDomain == null) {
            logger.warn("{}: no route domain found for router {} -- check 'service.router-domains' in configuration", name, routerName);
            return;
        }

        var currentDomain = serviceConfiguration.routerDomains().values().stream().filter(host::endsWith).findFirst();
        if (currentDomain.isEmpty()) {
            logger.warn("{}: could not detect current router from {} -- check 'service.router-domains' in configuration", name, host);
            return;
        }

        if (targetDomain.equals(currentDomain.get())) {
            logger.debug("{}: already has correct domain {}", name, targetDomain);
            return;
        }

        // on with patching
        var newHost = host.replace(currentDomain.get(), targetDomain);
        logger.info("{}: patching .spec.host from {} to {}", name, host, newHost);

        var newRoute = new RouteBuilder(route).editSpec().withHost(newHost).endSpec().build();
        logger.info("Patching route {} to {}", route, newRoute);
        // openShiftClient.routes().inNamespace(route.getMetadata().getNamespace()).withName(name).patch(newRoute);

        // emit event for the route
        var note = String.format("Patched .spec.host from %s to %s based on namespace router %s", host, newHost, routerName);
        KubernetesHelper.createEvent(openShiftClient, serviceConfiguration.instanceName(), route.getMetadata().getNamespace(), routeRef, note);
    }

    private static boolean isControlledNamespace(ServiceConfiguration serviceConfiguration, Namespace namespace) {
        // check if this namespace matches filters
        var nsLabels = Optional.ofNullable(namespace.getMetadata().getLabels()).orElse(Map.of());
        var nsFilterLabels = serviceConfiguration.namespaceFilters();
        var matchedLabels = nsFilterLabels.entrySet().stream().filter(r -> r.getValue().equals(nsLabels.get(r.getKey())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        if (matchedLabels.size() != nsFilterLabels.size()) {
            logger.debug("{}: namespace is missing labels {} -- ignoring patching", namespace.getMetadata().getName(), matchedLabels);
            return false;
        } else
            return true;
    }

    private static String namespaceRouter(ServiceConfiguration serviceConfiguration, Namespace namespace) {
        return KubernetesHelper.labelValue(namespace, serviceConfiguration.namespaceRouterLabel()).orElse(serviceConfiguration.defaultRouter());
    }

    private static ObjectReference referenceForRoute(Route route) {
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
