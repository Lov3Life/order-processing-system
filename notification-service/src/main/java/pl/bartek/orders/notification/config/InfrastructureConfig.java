package pl.bartek.orders.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pl.bartek.orders.outbox.config.KafkaRetryConfig;

@Configuration(proxyBeanMethods = false)
@Import(KafkaRetryConfig.class)
public class InfrastructureConfig {
}
