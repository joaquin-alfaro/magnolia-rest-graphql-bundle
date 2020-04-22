package com.formentor.magnolia.config.source.graphql;

import info.magnolia.cms.util.ExceptionUtil;
import info.magnolia.config.registry.AbstractDefinitionProviderWrapper;
import info.magnolia.config.registry.DefinitionMetadataBuilder;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.config.registry.DefinitionProviderBuilder;
import info.magnolia.config.registry.DefinitionRawView;
import info.magnolia.config.source.raw.DefinitionRawViewMapWrapper;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.resourceloader.Resource;
import info.magnolia.resourceloader.ResourceOrigin;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
public class GraphQLDefinitionProvider <T> extends AbstractDefinitionProviderWrapper<T> {
    private static final int MODIFICATION_CHECK_THRESHOLD = 1000;

    private final GraphQLConfigurationSource<T> relatedSource;
    private final String resourcePath;
    private final ResourceOrigin<?> resourceOrigin;
    private final Map2BeanTransformer map2BeanTransformer;

    private long lastModificationCheck = -1;
    private long lastResolved = -1;

    private DefinitionProvider<T> delegate;

    public GraphQLDefinitionProvider(GraphQLConfigurationSource<T> relatedSource, Resource graphQLResource, Map2BeanTransformer map2BeanTransformer) {
        this.relatedSource = relatedSource;
        this.map2BeanTransformer = map2BeanTransformer;
        this.resourcePath = graphQLResource.getPath();
        this.resourceOrigin = graphQLResource.getOrigin();
    }

    @Override
    public long getLastModified() {
        return resourceOrigin.getByPath(resourcePath).getLastModified();
    }

    @Override
    protected DefinitionProvider<T> getDelegate() {
        long time = System.currentTimeMillis();

        if (time - lastModificationCheck < MODIFICATION_CHECK_THRESHOLD) {
            return delegate;
        }

        lastModificationCheck = time;
        if (resourceOrigin.hasPath(resourcePath)) {
            if (getLastModified() > lastResolved) {
                delegate = resolve();
            }
        } else {
            log.debug("Resource origin no longer contains the definition file [{}], which has most likely been removed, will not attempt to re-resolve definition");
        }

        return delegate;
    }

    private DefinitionProvider<T> resolve() {
        final Resource graphQLResource = resourceOrigin.getByPath(resourcePath);
        final DefinitionProviderBuilder<T> builder = relatedSource.getRegistry().newDefinitionProviderBuilder();

        final DefinitionMetadataBuilder metadataBuilder = relatedSource.createMetadata(graphQLResource);

        String sdl = null;
        try {
            sdl = readGraphQL(graphQLResource);
        } catch (Exception e) {
            final String errorMessage =
                    ExceptionUtil.exceptionToWords(
                            Optional
                                    .ofNullable(ExceptionUtils.getRootCause(e))
                                    .orElse(e));

            builder.addProblem(
                    DefinitionProvider.Problem
                            .severe()
                            .withType(Problem.DefaultTypes.RESOLUTION)
                            .withTitle(String.format("Parsing configuration data from [%s] failed", resourcePath))
                            .withDetails(String.format("Failed to parse GRAPHQL file:%n%s", errorMessage))
                            .withRelatedException(e)
                            .build());

        }

        // rawView
        // key: sdl
        // value: content of the file as String
        final HashMap<String, Object> map = new HashMap();
        if (sdl != null) {
            map.put("sdl", sdl);
        }

        final DefinitionRawView raw = new DefinitionRawViewMapWrapper(map);
        builder.rawView(raw);

        builder.metadata(metadataBuilder);
        Class definitionType = metadataBuilder.getType().baseClass();

        return builder.buildFromTransformationResult(map2BeanTransformer.transform(map, definitionType));
    }

    /**
     * It could be possible to return the TypeRegistry of graphql-java but I think this operation is responsability of GraphQLProvider
     * @return
     */
    private String readGraphQL(final Resource res) throws IOException {
        Reader reader = res.openReader();
        StringBuilder sdl = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            sdl.append((char)ch);
        }

        return sdl.toString();
    }
}