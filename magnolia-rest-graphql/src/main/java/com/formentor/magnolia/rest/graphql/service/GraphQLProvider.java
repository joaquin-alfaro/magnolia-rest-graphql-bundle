package com.formentor.magnolia.rest.graphql.service;

import com.formentor.magnolia.rest.graphql.type.GraphQLType;
import com.formentor.magnolia.rest.graphql.type.Query;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

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
     * @param sdl
     * @return
     */
    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    public TypeDefinitionRegistry buildTypeRegistry() throws IOException, URISyntaxException {
        // 1. Add the core graphql schema
        URL coreGraphql = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(coreGraphql, Charsets.UTF_8);
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);

        // 2. Add the rest of graphql schemas
        URL schemas = Resources.getResource("restGraphql");
        File folder = new File(schemas.toURI());
        for (File file: folder.listFiles()) {
            String newsdl = Resources.toString(file.toURI().toURL(), Charsets.UTF_8);
            TypeDefinitionRegistry newtypeRegistry = new SchemaParser().parse(newsdl);
            mergeTypeRegistry(newtypeRegistry, typeRegistry);
        }
        return typeRegistry;
    }

    private void mergeTypeRegistry(TypeDefinitionRegistry from, TypeDefinitionRegistry to) {
        for (ObjectTypeDefinition type: from.getTypes(ObjectTypeDefinition.class)) {
            mergeTypeInto(type, to);
        }
    }

    private void mergeTypeInto(ObjectTypeDefinition newType, TypeDefinitionRegistry registry) {
        Optional<ObjectTypeDefinition> candidate = registry.getTypes(ObjectTypeDefinition.class)
                .stream()
                .filter(type -> type.getName().equals(newType.getName())).findFirst();
        // 1. If exists, add field definitions, else add the type
        if (candidate.isPresent()) {
            List<FieldDefinition> fieldDefinitions = candidate.get().getFieldDefinitions();
            fieldDefinitions.addAll(newType.getFieldDefinitions());

            ObjectTypeDefinition newObjectTypeDefinition = candidate.get().transform(builder -> builder.fieldDefinitions(fieldDefinitions));
            registry.remove(candidate.get());
            registry.add(newObjectTypeDefinition);
        } else {
        // 2. If does not exist, add it
            registry.add(newType);
        }
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
