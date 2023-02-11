package routepatchcontroller;

import io.fabric8.kubernetes.api.model.LabelSelector;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class InnerServiceConfiguration {

    @Inject
    ServiceConfiguration serviceConfiguration;

    @Produces
    @Singleton
    public Configuration inner() {
        final var namespaceLabelSelector = serviceConfiguration.namespaceLabelSelector().map(KubernetesHelper::buildLabelSelectorFromString);
        final var instanceName = Optional.ofNullable(System.getenv("HOSTNAME")).orElse("unknown");
        return new Configuration(namespaceLabelSelector, instanceName);
    }

    public static class Configuration {
        public final Optional<LabelSelector> namespaceLabelSelector;
        public final String instanceName;
        public Configuration(Optional<LabelSelector> namespaceLabelSelector, String instanceName) {
            this.namespaceLabelSelector = namespaceLabelSelector;
            this.instanceName = instanceName;
        }
    }
}
