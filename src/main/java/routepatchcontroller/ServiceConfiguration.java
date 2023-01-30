package routepatchcontroller;

import io.smallrye.config.ConfigMapping;

import java.util.Map;

@ConfigMapping(prefix="service")
public interface ServiceConfiguration {

    Map<String, String> routerDomains();

    String namespaceRouterLabel();

    String defaultRouter();

    String namespaceLabelSelector();

    String instanceName();
}
