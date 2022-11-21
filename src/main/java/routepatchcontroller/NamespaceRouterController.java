package routepatchcontroller;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NamespaceRouterController implements ResourceEventHandler<Namespace> {

    private final RoutePatcherService routePatcherService;
    private final ServiceConfiguration serviceConfiguration;

    @Inject
    public NamespaceRouterController(RoutePatcherService routePatcherService, ServiceConfiguration serviceConfiguration) {
        this.routePatcherService = routePatcherService;
        this.serviceConfiguration = serviceConfiguration;
    }

    @Override
    public void onAdd(Namespace obj) {
        routePatcherService.patchNamespace(obj);
    }

    @Override
    public void onUpdate(Namespace oldObj, Namespace newObj) {
        var routerLabel = serviceConfiguration.namespaceRouterLabel();
        var oldLabel = KubernetesHelper.labelValue(oldObj, routerLabel);
        var newLabel = KubernetesHelper.labelValue(newObj, routerLabel);
        if (!oldLabel.equals(newLabel)) // only patch if router label has changed
            routePatcherService.patchNamespace(newObj);
    }

    @Override
    public void onDelete(Namespace obj, boolean deletedFinalStateUnknown) {
        // do nothing
    }
}
