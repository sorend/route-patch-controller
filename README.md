# openshift-route-patch-controller -- a controller for patching Openshift Routes if there are multiple ingress controllers.

There is a bug in Openshift, so when you run multiple Ingress controllers (more external endpoints), then routes will be
created on the default ingress controller instead of the ingress controller designated to the namespace.

This controller patches routes .spec.host which are created for the wrong ingress controller.

## Installation

See [deploy](deploy/)

You need to edit [deploy/controller.yaml](deploy/controller.yaml) before applying. The application.properties
configmap must match your setup.

```yaml
application.properties: |-
  service.namespace-router-label = router
  service.default-router = apps
  service.router-domains."apps" = apps.mycluster.com
  service.router-domains."prod" = prod.mycluster.com
  service.router-domains."dev" = dev.mycluster.com
```

* `service.namespace-router-label` -- this is the label on the Namespace which is used to control which ingress controller 
  routes in the namespace should use.
* `service.default-router` -- this is the default router if there is no label on the Namespace.
* `service.router-domains."domain"` -- these specify the domain-postfix which each ingress controller uses.
