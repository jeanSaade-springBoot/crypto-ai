package com.fix132.approval;

import com.crypto.shared.SharedMarketSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Boot the actual isolated entry point; substitute ONLY the remote reader and
 * local test datasource. No normal Trader application component scan is allowed. */
class CutoverApprovalIsolationTest {
    @Test void consoleHasSecurityButNoTradingOrScheduledWorkers() {
        var source=mock(SharedMarketSource.class);when(source.mode()).thenReturn("OBSERVE");
        var app=new SpringApplication(CutoverApprovalApplication.class);
        app.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{
            var registry=(BeanDefinitionRegistry)factory;
            for(String name:registry.getBeanDefinitionNames()) {
                if(SharedMarketSource.class.getName().equals(registry.getBeanDefinition(name).getBeanClassName())) {
                    registry.removeBeanDefinition(name);
                    registry.registerBeanDefinition(name,new RootBeanDefinition(SharedMarketSource.class,()->source));
                }
            }
        }));
        try(var context=app.run("--server.address=127.0.0.1","--server.port=0","--shared-market.mode=OBSERVE",
            "--spring.datasource.url=jdbc:h2:mem:approval-isolation;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--spring.flyway.enabled=false","--spring.jmx.enabled=false")) {
            assertNotNull(context.getBean(CutoverApprovalService.class));
            assertNotNull(context.getBean(org.springframework.security.web.SecurityFilterChain.class));
            assertTrue(context.getBeansOfType(com.crypto.shared.SharedMarketConsumer.class).isEmpty());
            assertTrue(context.getBeansOfType(com.crypto.wallet.service.WalletAutoExecutionService.class).isEmpty());
            assertTrue(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty());
            assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        }
    }
}
