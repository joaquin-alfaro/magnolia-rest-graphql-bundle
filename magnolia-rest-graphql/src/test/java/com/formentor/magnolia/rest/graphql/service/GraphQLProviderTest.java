package com.formentor.magnolia.rest.graphql.service;

import com.formentor.magnolia.rest.graphql.type.Query;
import graphql.Assert;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import info.magnolia.demo.travel.tours.service.TourServices;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

public class GraphQLProviderTest {

    private GraphQLProvider graphQLProvider;

    @Mock
    TourServices tourServices;

    @Before
    public void setUp() {

        this.graphQLProvider = new GraphQLProvider(new Query(tourServices));
    }

    @Test
    public void whenBuildSchemaThenImplicitTypesAreCreated() throws IOException, URISyntaxException {
        TypeDefinitionRegistry typeDefinitionRegistry = graphQLProvider.buildTypeRegistry();
        Optional<TypeDefinition> typeDefinition = typeDefinitionRegistry.getType("Page");
        Assert.assertTrue(typeDefinition.isPresent());
    }
}
