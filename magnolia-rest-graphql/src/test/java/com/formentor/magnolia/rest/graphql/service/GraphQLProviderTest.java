package com.formentor.magnolia.rest.graphql.service;

import graphql.Assert;
import graphql.language.TypeDefinition;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class GraphQLProviderTest {

    private GraphQLProvider graphQLProvider;

    @Before
    public void setUp() {
        this.graphQLProvider = new GraphQLProvider();
    }

//    @Test
    public void whenAddsPageSchemaThenNewTypeInGrapQLSchema() throws IOException {
        String sdl = FileUtils.readFileToString(new File("src/test/resources/schema-tours.graphqls"), "utf-8");
        graphQLProvider.addSchema(sdl);

        Optional<TypeDefinition> type = graphQLProvider.getTypeRegistry().getType("Tour");
//        Assert.assertTrue(type.isPresent());
    }


//    @Test
    public void givenObjectWithDirectiveThenDirective() {
        Optional<TypeDefinition> type = graphQLProvider.getTypeRegistry().getType("Page");
//        Assert.assertTrue(type.isPresent());
    }
}
