package com.formentor.magnolia.config.source.graphql;

import info.magnolia.config.registry.Registry;
import info.magnolia.config.source.AbstractConfigurationSourceBuilder;
import info.magnolia.init.MagnoliaConfigurationProperties;
import info.magnolia.map2bean.Map2BeanTransformer;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.resourceloader.ResourceOrigin;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
public class GraphQLConfigurationSourceBuilder extends AbstractConfigurationSourceBuilder {

    /**
     * Regex format for config files according to Magnolia module conventions.
     * Mind the %s part of the regex, which should be replaced with the expected definition type.
     * @see #moduleConventionsFormatFor(Registry)
     */
    private static final String MODULE_CONVENTIONS_FORMAT = "^/(?<module>[a-zA-Z0-9-_]+)/(?<deftype>%s)/(?<relPath>.*/)?(?<name>[a-zA-Z0-9-_\\.]+)\\.graphql$";

    private final ResourceOrigin origin;
    private final Map2BeanTransformer map2BeanTransformer;
    private final MagnoliaConfigurationProperties magnoliaConfigurationProperties;
    private final ModuleRegistry moduleRegistry;

    public GraphQLConfigurationSourceBuilder(ResourceOrigin origin, Map2BeanTransformer map2BeanTransformer, MagnoliaConfigurationProperties magnoliaConfigurationProperties, ModuleRegistry moduleRegistry) {
        this.origin = origin;
        this.map2BeanTransformer = map2BeanTransformer;
        this.magnoliaConfigurationProperties = magnoliaConfigurationProperties;
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public void bindTo(Registry<?> registry) {
        if (registry == null) {
            throw new NullPointerException("Must pass a registry instance");
        }
        final Pattern pathPattern = validatePathPattern(moduleConventionsFormatFor(registry));

        new GraphQLConfigurationSource<>(origin, map2BeanTransformer, registry, pathPattern, magnoliaConfigurationProperties, moduleRegistry).start();
    }

    private Pattern validatePathPattern(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.warn("Pattern {} is invalid: {}", regex, e.getMessage());
            return Pattern.compile("$^");
        }
    }

    protected String moduleConventionsFormatFor(Registry registry) {
        return String.format(MODULE_CONVENTIONS_FORMAT, registry.type().getPluralName());
    }
}
