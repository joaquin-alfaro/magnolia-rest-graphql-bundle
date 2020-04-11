package com.formentor.magnolia.rest.graphql.service;

import com.formentor.magnolia.rest.graphql.type.GraphQLType;
import com.formentor.magnolia.rest.graphql.type.Query;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Slf4j
public class GraphQLProvider {

    private final Query query;
    private GraphQL graphQL;

    @Inject
    public GraphQLProvider(Query query) {
        this.query = query;
    }

    @PostConstruct
    private void init() {
        try {
            URL url = Resources.getResource("schema.graphqls");
            String sdl = Resources.toString(url, Charsets.UTF_8);
            GraphQLSchema graphQLSchema = buildSchema(sdl);
            graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        } catch (IOException e) {
            log.error("ERRORS during initialization of GraphQL");
        }
    }

    public Object execute(String query) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build();
        ExecutionResult executionResult = graphQL.execute(executionInput);

        return executionResult.toSpecification();
    }

    /**
     * Builds schema
     *
     * TODO
     * Builds schema dynamically from classes and annotations
     * @param sdl
     * @return
     */
    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(buildGraphQLType(query))
                .build();
    }

    private TypeRuntimeWiring.Builder buildGraphQLType(GraphQLType graphQLType) {
        TypeRuntimeWiring.Builder builder = newTypeWiring(graphQLType.getName());
        graphQLType.getFields().forEach((name, fetcher) -> builder.dataFetcher(name, fetcher));

        return builder;
    }

}
