package routepatchcontroller;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix="service")
public interface ServiceConfiguration {
    Optional<String> namespaceLabelSelector(); // only eval routes in namespaces matching this selector
}
