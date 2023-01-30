package routepatchcontroller;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
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

    private static final Logger logger = LoggerFactory.getLogger(RouteReconciler.class);

    @Inject
    OpenShiftClient openShiftClient;

    @Inject
    ServiceConfiguration serviceConfiguration;

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
                        .withLabelSelector(serviceConfiguration.namespaceLabelSelector())
                        .withSecondaryToPrimaryMapper(routesForNamespace)
                        .build();
        return EventSourceInitializer.nameEventSources(new InformerEventSource<>(configuration, context));
    }

    @Override
    public UpdateControl<Route> reconcile(Route resource, Context<Route> context) throws Exception {
        final var name = resource.getMetadata().getName();
        final var namespace = context.getSecondaryResource(Namespace.class).orElse(null);
        if (namespace == null) {
            logger.debug("{}: Route is not in namespace with labelSelector {} -- ignoring", name, serviceConfiguration.namespaceLabelSelector());
            return UpdateControl.noUpdate();
        }

        final var host = resource.getSpec().getHost();
        final var routeRef = KubernetesEventHelper.referenceForObj(resource);

        // find router name from namespace label
        final var routerName = Optional.ofNullable(namespace.getMetadata().getLabels()).flatMap(labels -> Optional.ofNullable(labels.get(serviceConfiguration.namespaceRouterLabel()))).orElse(serviceConfiguration.defaultRouter());

        final var targetDomain = serviceConfiguration.routerDomains().get(routerName);
        if (targetDomain == null) {
            logger.warn("{}: no route domain found for router {} -- check 'service.router-domains' in configuration", name, routerName);
            return UpdateControl.noUpdate();
        }

        final var currentDomainOpt = serviceConfiguration.routerDomains().values().stream().filter(host::endsWith).findFirst();
        if (currentDomainOpt.isEmpty()) {
            logger.warn("{}: could not detect current router from {} -- check 'service.router-domains' in configuration", name, host);
            return UpdateControl.noUpdate();
        }

        final var currentDomain = currentDomainOpt.get();
        if (targetDomain.equals(currentDomain)) {
            logger.debug("{}: already has correct domain {}", name, targetDomain);
            return UpdateControl.noUpdate();
        }

        //
        final var newHost = host.replace(currentDomain, targetDomain);
        logger.info("{}: patching .spec.host from {} to {}", name, host, newHost);

        final var newRoute = new RouteBuilder(resource).editSpec().withHost(newHost).endSpec().build();
        logger.debug("Patching route {} to {}", resource, newRoute);

        // emit event for the route
        final var note = String.format("Patched .spec.host from %s to %s based on namespace router %s", host, newHost, routerName);
        KubernetesEventHelper.createEvent(openShiftClient, serviceConfiguration.instanceName(), resource.getMetadata().getNamespace(), routeRef, note);

        // done
        return UpdateControl.patchResourceAndStatus(newRoute);
    }
}
