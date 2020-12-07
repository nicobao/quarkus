package io.quarkus.hibernate.orm.runtime;

import java.util.List;
import java.util.Map;

import io.quarkus.hibernate.orm.runtime.integration.HibernateOrmIntegrationRuntimeInitListener;

public final class PersistenceProviderSetup {

    private PersistenceProviderSetup() {
        // not to be constructed
    }

    public static void registerStaticInitPersistenceProvider() {
        javax.persistence.spi.PersistenceProviderResolverHolder
                .setPersistenceProviderResolver(new StaticInitHibernatePersistenceProviderResolver());
    }

    public static void registerRuntimePersistenceProvider(HibernateOrmRuntimeConfig hibernateOrmRuntimeConfig,
            Map<String, List<HibernateOrmIntegrationRuntimeInitListener>> integrationRuntimeInitListeners) {
        javax.persistence.spi.PersistenceProviderResolverHolder.setPersistenceProviderResolver(
                new SingletonPersistenceProviderResolver(
                        new FastBootHibernatePersistenceProvider(hibernateOrmRuntimeConfig, integrationRuntimeInitListeners)));
    }
}
