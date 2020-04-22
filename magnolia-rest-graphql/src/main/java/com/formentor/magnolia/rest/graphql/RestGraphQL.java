package com.formentor.magnolia.rest.graphql;

import com.formentor.magnolia.config.source.graphql.GraphQLConfigurationSourceBuilder;
import com.formentor.magnolia.rest.graphql.registry.GraphQLEndpointDefinitionRegistry;
import info.magnolia.init.MagnoliaConfigurationProperties;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.module.ModuleLifecycle;
import info.magnolia.module.ModuleLifecycleContext;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.resourceloader.ResourceOrigin;

import javax.inject.Inject;

/**
 * This class is optional and represents the configuration for the magnolia-rest-graphql module.
 * By exposing simple getter/setter/adder methods, this bean can be configured via content2bean
 * using the properties and node from <tt>config:/modules/magnolia-rest-graphql</tt>.
 * If you don't need this, simply remove the reference to this class in the module descriptor xml.
 * See https://documentation.magnolia-cms.com/display/DOCS/Module+configuration for information about module configuration.
 */
public class RestGraphQL implements ModuleLifecycle {
    private final GraphQLEndpointDefinitionRegistry graphQLRegistry;
    /**
     * There is no Factory for grapql files, so these components are injected in RestGraphQL
     */
    private final ResourceOrigin origin;
    private final Map2BeanTransformer map2BeanTransformer;
    private final ModuleRegistry moduleRegistry;
    private final MagnoliaConfigurationProperties magnoliaConfigurationProperties;

    @Inject
    public RestGraphQL(GraphQLEndpointDefinitionRegistry graphQLRegistry, ResourceOrigin origin, Map2BeanTransformer map2BeanTransformer, MagnoliaConfigurationProperties magnoliaConfigurationProperties, ModuleRegistry moduleRegistry) {
        this.graphQLRegistry = graphQLRegistry;
        this.origin = origin;
        this.map2BeanTransformer = map2BeanTransformer;
        this.magnoliaConfigurationProperties = magnoliaConfigurationProperties;
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public void start(ModuleLifecycleContext moduleLifecycleContext) {
        new GraphQLConfigurationSourceBuilder(origin, map2BeanTransformer,magnoliaConfigurationProperties, moduleRegistry).bindTo(graphQLRegistry);
    }

    @Override
    public void stop(ModuleLifecycleContext moduleLifecycleContext) {

    }
}
