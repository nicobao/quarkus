package io.quarkus.resteasy.reactive.server.deployment;

import static org.jboss.resteasy.reactive.common.processor.ResteasyReactiveDotNames.STRING;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.resteasy.reactive.server.core.Deployment;
import org.jboss.resteasy.reactive.server.core.parameters.converters.GeneratedParameterConverter;
import org.jboss.resteasy.reactive.server.core.parameters.converters.NoopParameterConverter;
import org.jboss.resteasy.reactive.server.core.parameters.converters.ParameterConverter;
import org.jboss.resteasy.reactive.server.core.parameters.converters.ParameterConverterSupplier;
import org.jboss.resteasy.reactive.server.core.parameters.converters.RuntimeResolvedConverter;
import org.jboss.resteasy.reactive.server.model.ServerResourceMethod;
import org.jboss.resteasy.reactive.server.processor.ServerEndpointIndexer;
import org.jboss.resteasy.reactive.server.processor.ServerIndexedParameter;

import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class QuarkusServerEndpointIndexer
        extends ServerEndpointIndexer {
    private final MethodCreator initConverters;
    private final BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer;
    private final BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildProducer;
    private static final Set<DotName> CONTEXT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            DotName.createSimple(HttpServerRequest.class.getName()),
            DotName.createSimple(HttpServerResponse.class.getName()),
            DotName.createSimple(RoutingContext.class.getName()))));

    QuarkusServerEndpointIndexer(Builder builder) {
        super(builder);
        this.initConverters = builder.initConverters;
        this.generatedClassBuildItemBuildProducer = builder.generatedClassBuildItemBuildProducer;
        this.bytecodeTransformerBuildProducer = builder.bytecodeTransformerBuildProducer;
    }

    protected boolean isContextType(ClassType klass) {
        return super.isContextType(klass) || CONTEXT_TYPES.contains(klass.name());
    }

    @Override
    protected ParameterConverterSupplier extractConverter(String elementType, IndexView indexView,
            Map<String, String> existingConverters, String errorLocation, boolean hasRuntimeConverters) {
        if (elementType.equals(String.class.getName())) {
            if (hasRuntimeConverters)
                return new RuntimeResolvedConverter.Supplier().setDelegate(new NoopParameterConverter.Supplier());
            // String needs no conversion
            return null;
        } else if (existingConverters.containsKey(elementType)) {
            String className = existingConverters.get(elementType);
            ParameterConverterSupplier delegate;
            if (className == null)
                delegate = null;
            else
                delegate = new GeneratedParameterConverter().setClassName(className);
            if (hasRuntimeConverters)
                return new RuntimeResolvedConverter.Supplier().setDelegate(delegate);
            if (delegate == null)
                throw new RuntimeException("Failed to find converter for " + elementType);
            return delegate;
        }

        MethodDescriptor fromString = null;
        MethodDescriptor valueOf = null;
        MethodInfo stringCtor = null;
        String primitiveWrapperType = primitiveTypes.get(elementType);
        String prefix = "";
        if (primitiveWrapperType != null) {
            valueOf = MethodDescriptor.ofMethod(primitiveWrapperType, "valueOf", primitiveWrapperType, String.class);
            prefix = "io.quarkus.generated.";
        } else {
            ClassInfo type = indexView.getClassByName(DotName.createSimple(elementType));
            if (type != null) {
                for (MethodInfo i : type.methods()) {
                    if (i.parameters().size() == 1) {
                        if (i.parameters().get(0).name().equals(STRING)) {
                            if (i.name().equals("<init>")) {
                                stringCtor = i;
                            } else if (i.name().equals("valueOf")) {
                                valueOf = MethodDescriptor.of(i);
                            } else if (i.name().equals("fromString")) {
                                fromString = MethodDescriptor.of(i);
                            }
                        }
                    }
                }
                if (type.isEnum()) {
                    //spec weirdness, enums order is different
                    if (fromString != null) {
                        valueOf = null;
                    }
                }
            }
        }

        String baseName;
        ParameterConverterSupplier delegate;
        if (stringCtor != null || valueOf != null || fromString != null) {
            baseName = prefix + elementType + "$quarkusrestparamConverter$";
            try (ClassCreator classCreator = new ClassCreator(
                    new GeneratedClassGizmoAdaptor(generatedClassBuildItemBuildProducer, true), baseName, null,
                    Object.class.getName(), ParameterConverter.class.getName())) {
                MethodCreator mc = classCreator.getMethodCreator("convert", Object.class, Object.class);
                if (stringCtor != null) {
                    ResultHandle ret = mc.newInstance(stringCtor, mc.getMethodParam(0));
                    mc.returnValue(ret);
                } else if (valueOf != null) {
                    ResultHandle ret = mc.invokeStaticMethod(valueOf, mc.getMethodParam(0));
                    mc.returnValue(ret);
                } else if (fromString != null) {
                    ResultHandle ret = mc.invokeStaticMethod(fromString, mc.getMethodParam(0));
                    mc.returnValue(ret);
                }
            }
            delegate = new GeneratedParameterConverter().setClassName(baseName);
        } else {
            // let's not try this again
            baseName = null;
            delegate = null;
        }
        existingConverters.put(elementType, baseName);
        if (hasRuntimeConverters)
            return new RuntimeResolvedConverter.Supplier().setDelegate(delegate);
        if (delegate == null)
            throw new RuntimeException("Failed to find converter for " + elementType);
        return delegate;
    }

    @Override
    protected ServerResourceMethod createResourceMethod() {
        return new ServerResourceMethod();
    }

    protected void handleFieldExtractors(String currentTypeName, Map<FieldInfo, ServerIndexedParameter> fieldExtractors,
            boolean superTypeIsInjectable) {
        bytecodeTransformerBuildProducer.produce(new BytecodeTransformerBuildItem(currentTypeName,
                new ClassInjectorTransformer(fieldExtractors, superTypeIsInjectable)));
    }

    protected void handleConverter(String currentTypeName, FieldInfo field) {
        initConverters.invokeStaticMethod(MethodDescriptor.ofMethod(currentTypeName,
                ClassInjectorTransformer.INIT_CONVERTER_METHOD_NAME + field.name(),
                void.class, Deployment.class),
                initConverters.getMethodParam(0));
    }

    public static final class Builder extends AbstractBuilder<Builder> {

        private BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer;
        private BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildProducer;
        private MethodCreator initConverters;

        @Override
        public QuarkusServerEndpointIndexer build() {
            return new QuarkusServerEndpointIndexer(this);
        }

        public MethodCreator getInitConverters() {
            return initConverters;
        }

        public Builder setBytecodeTransformerBuildProducer(
                BuildProducer<BytecodeTransformerBuildItem> bytecodeTransformerBuildProducer) {
            this.bytecodeTransformerBuildProducer = bytecodeTransformerBuildProducer;
            return this;
        }

        public Builder setGeneratedClassBuildItemBuildProducer(
                BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer) {
            this.generatedClassBuildItemBuildProducer = generatedClassBuildItemBuildProducer;
            return this;
        }

        public Builder setInitConverters(MethodCreator initConverters) {
            this.initConverters = initConverters;
            return this;
        }
    }
}
