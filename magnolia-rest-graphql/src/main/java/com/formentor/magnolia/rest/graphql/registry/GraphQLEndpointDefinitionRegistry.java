package com.formentor.magnolia.rest.graphql.registry;

import com.formentor.magnolia.rest.graphql.GraphQLDefinition;
import info.magnolia.config.registry.AbstractRegistry;
import info.magnolia.config.registry.DefinitionMetadata;
import info.magnolia.config.registry.DefinitionMetadataBuilder;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.config.registry.DefinitionType;
import info.magnolia.event.EventBus;
import info.magnolia.event.SystemEventBus;
import info.magnolia.module.ModuleRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Set;

import static info.magnolia.rest.registry.EndpointDefinitionRegistryEventType.REGISTERED;

/**
 * Registry for {@linkplain GraphQLDefinition Graphql endpoint types}.
 */
@Singleton
@Slf4j
public class GraphQLEndpointDefinitionRegistry extends AbstractRegistry<GraphQLDefinition> {
    static final DefinitionType TYPE = new DefinitionType() {
        @Override
        @Deprecated
        public String name() {
            return null;
        }

        @Override
        public String getName() {
            return "graphql";
        }

        @Override
        public Class baseClass() {
            return GraphQLDefinition.class;
        }
    };

    private EventBus systemEventBus;

    @Inject
    public GraphQLEndpointDefinitionRegistry(ModuleRegistry moduleRegistry, @Named(SystemEventBus.NAME) EventBus systemEventBus) {
        super(moduleRegistry);
        this.systemEventBus = systemEventBus;
    }

    @Override
    public DefinitionType type() {
        return TYPE;
    }

    @Override
    public DefinitionMetadataBuilder newMetadataBuilder() {
        return new RelativePathMetadataBuilder();
    }

    @Override
    public void register(DefinitionProvider<GraphQLDefinition> provider) {
        DefinitionMetadata registeredMetadata = getRegistryMap().put(onRegister(provider));
        if (registeredMetadata != null) {
            systemEventBus.fireEvent(new GraphQLEndpointDefinitionRegistryEvent(REGISTERED, provider));
        }
    }

    @Override
    public Set<DefinitionMetadata> unregisterAndRegister(Collection<DefinitionMetadata> toRemoveIds, Collection<DefinitionProvider<GraphQLDefinition>> definitionProviders) {
        Set<DefinitionMetadata> registeredIds = super.unregisterAndRegister(toRemoveIds, definitionProviders);

        /**
         * TODO
         * Implements unregisterAndRegister
         */
        log.error("GraphQLEndpointDefinition.unregisterAndRegister() not supported yet !!");
        return registeredIds;
    }

    /**
     * This DefinitionMetadataBuilder generates referenceId from the relative-location only.
     * <p>This way, resource paths are dynamically configured/deduced from config path;
     */
    public static class RelativePathMetadataBuilder extends DefinitionMetadataBuilder {
        @Override
        protected String buildReferenceId() {
            return getRelativeLocation();
        }
    }
}
