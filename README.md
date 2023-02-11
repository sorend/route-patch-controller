# openshift-route-patch-controller -- a controller for patching Openshift Routes if there are multiple ingress controllers.

There is a bug in Openshift, so when you run multiple Ingress controllers (more external endpoints), then routes will be
created on the default ingress controller instead of the ingress controller designated to the namespace.

This controller patches routes .spec.host which are created for the wrong ingress controller.

## Installation

See [deploy](deploy/)

If you want the controller to watch only specific namespaces, then you can set the environment:
`SERVICE_NAMESPACE_LABEL_SELECTOR=business-project=bankdata`.
