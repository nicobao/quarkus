package io.quarkus.smallrye.opentracing.deployment;

import java.lang.reflect.Method;

import javax.enterprise.inject.spi.ObserverMethod;
import javax.servlet.DispatcherType;

import io.opentracing.contrib.interceptors.OpenTracingInterceptor;
import io.opentracing.contrib.jaxrs2.server.SpanFinishingFilter;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import io.quarkus.resteasy.reactive.spi.CustomContainerResponseFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.DynamicFeatureBuildItem;
import io.quarkus.smallrye.opentracing.runtime.QuarkusSmallRyeTracingDynamicFeature;
import io.quarkus.smallrye.opentracing.runtime.QuarkusSmallRyeTracingStandaloneContainerResponseFilter;
import io.quarkus.smallrye.opentracing.runtime.QuarkusSmallRyeTracingStandaloneVertxDynamicFeature;
import io.quarkus.smallrye.opentracing.runtime.TracerProducer;
import io.quarkus.undertow.deployment.FilterBuildItem;

public class SmallRyeOpenTracingProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return new AdditionalBeanBuildItem(OpenTracingInterceptor.class, TracerProducer.class);
    }

    @BuildStep
    ReflectiveMethodBuildItem registerMethod() throws Exception {
        Method isAsync = ObserverMethod.class.getMethod("isAsync");
        return new ReflectiveMethodBuildItem(isAsync);
    }

    @BuildStep
    void setupFilter(BuildProducer<ResteasyJaxrsProviderBuildItem> providers,
            BuildProducer<FilterBuildItem> filterProducer,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<CustomContainerResponseFilterBuildItem> customResponseFilters,
            BuildProducer<DynamicFeatureBuildItem> dynamicFeatures,
            Capabilities capabilities) {

        feature.produce(new FeatureBuildItem(Feature.SMALLRYE_OPENTRACING));

        providers.produce(new ResteasyJaxrsProviderBuildItem(QuarkusSmallRyeTracingDynamicFeature.class.getName()));

        if (capabilities.isPresent(Capability.SERVLET)) {
            FilterBuildItem filterInfo = FilterBuildItem.builder("tracingFilter", SpanFinishingFilter.class.getName())
                    .setAsyncSupported(true)
                    .addFilterUrlMapping("*", DispatcherType.FORWARD)
                    .addFilterUrlMapping("*", DispatcherType.INCLUDE)
                    .addFilterUrlMapping("*", DispatcherType.REQUEST)
                    .addFilterUrlMapping("*", DispatcherType.ASYNC)
                    .addFilterUrlMapping("*", DispatcherType.ERROR)
                    .build();
            filterProducer.produce(filterInfo);
        } else if (capabilities.isPresent(Capability.RESTEASY)) {
            providers.produce(
                    new ResteasyJaxrsProviderBuildItem(QuarkusSmallRyeTracingStandaloneVertxDynamicFeature.class.getName()));
        } else if (capabilities.isPresent(Capability.QUARKUS_REST)) {
            customResponseFilters.produce(new CustomContainerResponseFilterBuildItem(
                    QuarkusSmallRyeTracingStandaloneContainerResponseFilter.class.getName()));
            dynamicFeatures.produce(new DynamicFeatureBuildItem(QuarkusSmallRyeTracingDynamicFeature.class.getName()));
        }
    }

    @BuildStep
    public CapabilityBuildItem capability() {
        return new CapabilityBuildItem(Capability.SMALLRYE_OPENTRACING);
    }

}
