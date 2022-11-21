package routepatchcontroller;

import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.openshift.api.model.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoutePatchController implements ResourceEventHandler<Route> {

    private static final Logger logger = LoggerFactory.getLogger(RoutePatchController.class);

    private final RoutePatcherService routePatcherService;

    @Inject
    public RoutePatchController(RoutePatcherService routePatcherService) {
        this.routePatcherService = routePatcherService;
    }

    @Override
    public void onAdd(Route obj) {
        routePatcherService.patchRoute(obj);
    }

    @Override
    public void onUpdate(Route oldObj, Route newObj) {
        routePatcherService.patchRoute(newObj);
    }

    @Override
    public void onDelete(Route obj, boolean deletedFinalStateUnknown) {
        // ignore, it's going to be deleted
    }

}
