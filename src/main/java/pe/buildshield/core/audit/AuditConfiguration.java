package pe.buildshield.core.audit;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.buildshield.core.audit.infrastructure.AccessDenialFilter;

@Configuration(proxyBeanMethods = false)
public class AuditConfiguration {
    @Bean FilterRegistrationBean<AccessDenialFilter> accessDenialFilter(AuditTrail audit) {
        var registration = new FilterRegistrationBean<>(new AccessDenialFilter(audit));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
