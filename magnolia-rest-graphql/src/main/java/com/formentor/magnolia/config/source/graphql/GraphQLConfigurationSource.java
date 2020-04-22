package com.formentor.magnolia.config.source.graphql;

import info.magnolia.config.registry.DefinitionMetadataBuilder;
import info.magnolia.config.registry.DefinitionProviderProblemLogger;
import info.magnolia.config.registry.Registry;
import info.magnolia.config.source.yaml.AbstractFileResourceConfigurationSource;
import info.magnolia.init.MagnoliaConfigurationProperties;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.resourceloader.Resource;
import info.magnolia.resourceloader.ResourceOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class GraphQLConfigurationSource<T> extends AbstractFileResourceConfigurationSource<T> {
    private static Logger log = LoggerFactory.getLogger(GraphQLConfigurationSource.class);

    private final Map2BeanTransformer map2BeanTransformer;
    private final MagnoliaConfigurationProperties magnoliaConfigurationProperties;

    public GraphQLConfigurationSource(ResourceOrigin origin,
                                      Map2BeanTransformer map2BeanTransformer,
                                      Registry<T> registry,
                                      Pattern pathPattern,
                                      MagnoliaConfigurationProperties magnoliaConfigurationProperties,
                                      ModuleRegistry moduleRegistry) {
        super(origin, registry, pathPattern, moduleRegistry);
        this.map2BeanTransformer = map2BeanTransformer;
        this.magnoliaConfigurationProperties = magnoliaConfigurationProperties;
    }

    @Override
    public void loadAndRegister(Resource resource) {
        if (resource == null) {
            throw new IllegalStateException("Resource cannot be null");
        }

        final GraphQLDefinitionProvider<T> definitionProvider = new GraphQLDefinitionProvider<>(this, resource, map2BeanTransformer);

        getRegistry().register(definitionProvider);

        log.info("Registered definition from YAML file [{}]: {}", resource.getPath(), definitionProvider.getMetadata());

        DefinitionProviderProblemLogger
                .withLoggingContext(log, magnoliaConfigurationProperties.getBooleanProperty("magnolia.develop"))
                .logProblems(definitionProvider);

    }

    @Override
    protected Registry<T> getRegistry() {
        return super.getRegistry();
    }

    @Override
    protected DefinitionMetadataBuilder createMetadata(Resource resource) {
        return super.createMetadata(resource);
    }
}
