package routepatchcontroller;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Startup
public class SharedIndexInformerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SharedIndexInformerConfiguration.class);

    private final OpenShiftClient openShiftClient;

    private final RoutePatchController routePatchController;

    private final NamespaceRouterController namespaceRouterController;

    @Inject
    public SharedIndexInformerConfiguration(OpenShiftClient openShiftClient, RoutePatchController routePatchController, NamespaceRouterController namespaceRouterController) {
        this.openShiftClient = openShiftClient;
        this.routePatchController = routePatchController;
        this.namespaceRouterController = namespaceRouterController;
    }

    public void startup(@Observes StartupEvent ev) {
        var routeInformer = openShiftClient.informers().sharedIndexInformerFor(Route.class, 60*60*1000);
        routeInformer.addEventHandler(routePatchController);
        var namespaceInformer = openShiftClient.informers().sharedIndexInformerFor(Namespace.class, 60*60*1000);
        namespaceInformer.addEventHandler(namespaceRouterController);
        openShiftClient.informers().startAllRegisteredInformers();
        logger.info("Informers started");
    }

    public void shutdown(@Observes StartupEvent ev) {
        openShiftClient.informers().stopAllRegisteredInformers();
        logger.info("Informers shutdown");
    }
}
