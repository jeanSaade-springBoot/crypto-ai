package com.crypto.wallet.service;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** FIX-125: fail before a shared-wallet write/locking read if the caller skipped
 * the global guard. This wraps Spring Data interfaces, including inherited save
 * methods. It is not authorization for arbitrary raw SQL or EntityManager use;
 * the named source-inventory test prohibits adding such wallet writers silently. */
@Component
public class WalletMutationRepositoryGuard implements BeanPostProcessor {
    private static final Set<String> GUARDED=Set.of("walletAssetRepository","walletDailyStatisticsRepository",
        "walletSettingsRepository","walletTradeRepository","walletCashFlowRepository","walletSnapshotRepository");
    public static void check(String repository,String method) {
        if(GUARDED.contains(repository) && (method.startsWith("save") || method.startsWith("delete") || method.equals("flush")
            || method.contains("ForUpdate") || method.equals("creditQuantity") || method.equals("debitQuantityIfSufficient")))
            WalletTransactionCoordination.requireMutation();
    }
    @Override public Object postProcessAfterInitialization(Object bean,String name) {
        if(!GUARDED.contains(name))return bean;
        ProxyFactory proxy=new ProxyFactory(bean);
        proxy.addAdvice((MethodInterceptor) call->{
            check(name,call.getMethod().getName());
            return call.proceed();
        });
        return proxy.getProxy();
    }
}
