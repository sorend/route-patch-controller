package routepatchcontroller;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.operator.v1.IngressController;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class RouteReconciler implements Reconciler<Route>, EventSourceInitializer<Route> {

    private static final String LABEL_SKIP = "ose.openshift.dk/skip-route-patch";

    private static final Logger logger = LoggerFactory.getLogger(RouteReconciler.class);

    @Inject
    OpenShiftClient openShiftClient;

    @Inject
    InnerServiceConfiguration.Configuration serviceConfiguration;

    /**
     * Creates a secondary resource of the namespace of the router. This ensures that when the namespace is updated,
     * the routes within the namespace will also be reconciled.
     */
    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Route> context) {
        final SecondaryToPrimaryMapper<Namespace> routesForNamespace = (Namespace t) -> context.getPrimaryCache()
                .list(route -> route.getMetadata().getNamespace().equals(t.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());
        final var configuration = InformerConfiguration.from(Namespace.class, context)
                .withSecondaryToPrimaryMapper(routesForNamespace)
                .build();
        return EventSourceInitializer.nameEventSources(new InformerEventSource<>(configuration, context));
    }

    @Override
    public UpdateControl<Route> reconcile(Route resource, Context<Route> context) throws Exception {
        final var name = resource.getMetadata().getName();
        final var namespaceName = resource.getMetadata().getNamespace();

        final var skipLabel = KubernetesHelper.labelValue(resource, LABEL_SKIP);
        if (skipLabel.isPresent()) {
            logger.info("{}/{}: Has label {} -- ignoring", name, namespaceName, LABEL_SKIP);
            return UpdateControl.noUpdate();
        }

        // find the namespace of the resource
        final var namespace = context.getSecondaryResource(Namespace.class).orElse(openShiftClient.namespaces().withName(namespaceName).get()); // get from cache or directly if fails
        if (namespace == null) {
            logger.warn("{}/{}: Namespace not found for Route -- ignoring", name, namespaceName);
            return UpdateControl.noUpdate();
        }

        final var includeNamespace = serviceConfiguration.namespaceLabelSelector.map(s -> KubernetesHelper.evalLabelSelectorOn(s, namespace)).orElse(true);
        if (!includeNamespace) {
            logger.debug("{}/{}: Namespace not matched by label selector -- ignoring", name, namespaceName);
            return UpdateControl.noUpdate();
        }

        // find which ingressController this resource belongs to
        final var ingressControllers = openShiftClient.resources(IngressController.class).inAnyNamespace().list().getItems();
        final var ingressController = ingressControllers.stream()
                .filter(x -> KubernetesHelper.evalLabelSelectorOn(x.getSpec().getRouteSelector(), resource) || KubernetesHelper.evalLabelSelectorOn(x.getSpec().getNamespaceSelector(), namespace))
                .findFirst().orElse(null);
        if (ingressController == null) {
            logger.debug("{}/{}: Route is not matched by any ingressController -- ignoring", name, namespaceName);
            return UpdateControl.noUpdate();
        }

        // find the domain of the ingress controller
        final var targetDomain = Optional.ofNullable(ingressController.getSpec().getDomain()).orElse(ingressController.getStatus().getDomain());
        final var host = resource.getSpec().getHost();
        final var currentDomain = host.substring(host.indexOf('.') + 1);

        if (targetDomain.equals(currentDomain)) {
            logger.debug("{}/{}: already has correct domain {}", name, namespaceName, targetDomain);
            return UpdateControl.noUpdate();
        }

        //
        final var newHost = host.replace(currentDomain, targetDomain);
        logger.info("{}: patching .spec.host from {} to {}", name, host, newHost);

        final var newRoute = new RouteBuilder(resource).editSpec().withHost(newHost).endSpec().build();
        logger.debug("Patching route {} to {}", resource, newRoute);

        // emit event for the route
        final var note = String.format("Patching .spec.host from %s to %s based on namespace router %s", host, newHost, ingressController.getMetadata().getName());
        final var routeRef = KubernetesHelper.referenceForObj(resource);
        KubernetesHelper.createEvent(openShiftClient, serviceConfiguration.instanceName, resource.getMetadata().getNamespace(), routeRef, "HostRouterMismatch", "Patched", note);

        // done
        return UpdateControl.patchResourceAndStatus(newRoute);
    }
}
