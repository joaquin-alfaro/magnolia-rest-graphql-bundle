package com.formentor.magnolia.rest.graphql.service;

import graphql.language.TypeDefinition;
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.rest.registry.EndpointDefinitionRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.BDDMockito.given;

public class GraphQLProviderTest {

    private GraphQLProvider graphQLProvider;

    @Mock
    private EndpointDefinitionRegistry endpointRegistry;

    @Mock
    private I18nContentSupport i18nContentSupport;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        given(endpointRegistry.getAllProviders()).willReturn(Collections.emptyList());

        graphQLProvider = new GraphQLProvider(endpointRegistry, i18nContentSupport);
    }

    @Test
    public void given_TypeRegistry_When_AddDelivery_Then_TypeAndDataFetcherAdded() throws IOException {
    // Given
        graphQLProvider.init();

        Optional<TypeDefinition> typeDefinition = graphQLProvider.getTypeRegistry().getType("Query");
        Assert.assertTrue(typeDefinition.isPresent());

        /*
        SchemaParser schemaParser = new SchemaParser();
        URL coreGraphql = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(coreGraphql, Charsets.UTF_8);
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(sdl);
        */
    // When

    // Then

    }
}
