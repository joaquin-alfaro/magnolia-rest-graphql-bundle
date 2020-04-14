package com.formentor.magnolia.rest.graphql.service;

import com.formentor.magnolia.rest.graphql.type.Node;
import com.formentor.magnolia.rest.graphql.type.NodeMap;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.StringValue;
import graphql.language.TypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import info.magnolia.context.MgnlContext;
import info.magnolia.rest.delivery.jcr.filter.NodeTypesPredicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Session;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Slf4j
@Getter
public class GraphQLProvider {

    private static final String QUERY = "Query";
    private GraphQL graphQL;
    private TypeDefinitionRegistry typeRegistry;

    @Inject
    public GraphQLProvider() {

    }

    @PostConstruct
    private void init() {
        try {
            typeRegistry = buildTypeRegistry();
            graphQL = buildGraphQLForTypeRegistry(typeRegistry);
        } catch (IOException e) {
            log.error("ERRORS during initialization of GraphQL");
        }
    }

    private GraphQL buildGraphQLForTypeRegistry(TypeDefinitionRegistry typeRegistry) {
        RuntimeWiring runtimeWiring = buildWiring(typeRegistry);
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    public Object execute(String query) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build();
        ExecutionResult executionResult = graphQL.execute(executionInput);

        return executionResult.toSpecification();
    }

    public void addSchema(String newsdl) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry newtypeRegistry = schemaParser.parse(newsdl);
        mergeTypeRegistry(newtypeRegistry, typeRegistry);

        RuntimeWiring runtimeWiring = buildWiring(typeRegistry);
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
        graphQL = graphQL.transform(builder -> builder.schema(graphQLSchema));
    }

    public TypeDefinitionRegistry buildTypeRegistry() throws IOException {
        SchemaParser schemaParser = new SchemaParser();
        // 1. Init typeRegistry with core graphql schema
        URL coreGraphql = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(coreGraphql, Charsets.UTF_8);
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(sdl);

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

    private RuntimeWiring buildWiring(TypeDefinitionRegistry typeRegistry) {
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        Optional<TypeRuntimeWiring.Builder> queryType = buildQueryType(typeRegistry);
        if (queryType.isPresent()) {
            builder.type(queryType.get());
        }
        return builder.build();
    }

    private Optional<TypeRuntimeWiring.Builder> buildQueryType(TypeDefinitionRegistry typeRegistry) {
        Optional<TypeDefinition> typeDefinitionQuery = typeRegistry.getType(QUERY);
        if (!typeDefinitionQuery.isPresent()) {
            return Optional.empty();
        }
        ObjectTypeDefinition typeQuery = (ObjectTypeDefinition)typeDefinitionQuery.get();
        TypeRuntimeWiring.Builder builder = newTypeWiring(QUERY);
        // 1. Add implicit field "nodes"
        builder.dataFetcher("nodes", buildDataFetcherNodes()
        );

        // 2. Create fetchers for the fields defined in the schema

        List<FieldDefinition> fields = typeQuery.getFieldDefinitions();
        fields.stream()
                .filter(field -> !field.getName().equals("nodes"))
                .forEach(field -> {
                    Optional<DataFetcher> dataFetcher = buildDataFetcherForField(field);
                    if (dataFetcher.isPresent()) {
                        builder.dataFetcher(field.getName(), dataFetcher.get());
                    }
                });

        return Optional.of(builder);
    }

    private DataFetcher buildDataFetcherNodes() {
        return dataFetchingEnvironment -> {
            String workspace = dataFetchingEnvironment.getArgument("workspace");
            String path = dataFetchingEnvironment.getArgument("path");

            Session session = MgnlContext.getJCRSession(workspace);
            javax.jcr.Node rootNode = session.getNode((path == null) ?"/": path);

            List<Node> nodes = new ArrayList<>();
            rootNode.getNodes().forEachRemaining(node -> nodes.add(new Node((javax.jcr.Node)node)));

            return nodes;
        };
    }
    
    private Optional<DataFetcher> buildDataFetcherForField(FieldDefinition field) {

        /**
         * Get definition from field directive
         *
         * directive @definition(workspace : String!, rootPath : String, nodeTypes : String) on FIELD_DEFINITION
         *
         */
        Directive definition = field.getDirective("definition");
        if (definition == null) {
            return Optional.empty();
        }

        Argument argWorkspace = definition.getArgument("workspace");
        if (argWorkspace == null) {
            return Optional.empty();
        }
        String workspace = ((StringValue)argWorkspace.getValue()).getValue();

        Argument argRootPath = definition.getArgument("rootPath");
        String path = (argRootPath == null)? "/": ((StringValue)argRootPath.getValue()).getValue();

        Argument argNodeTypes = definition.getArgument("nodeTypes");
        final List<String> nodeTypes;
        if (argNodeTypes == null){
            nodeTypes = Collections.emptyList();
        } else {
            nodeTypes = ((ArrayValue)argNodeTypes.getValue()).getValues()
                    .stream()
                    .map(value -> ((StringValue)value).getValue())
                    .collect(Collectors.toList());
        }

        DataFetcher dataFetcher = dataFetchingEnvironment -> {
            Session session = MgnlContext.getJCRSession(workspace);
            javax.jcr.Node rootNode = session.getNode((path == null) ?"/": path);

            final List<NodeMap> nodes = new ArrayList<>();
            rootNode.getNodes().forEachRemaining(node -> nodes.add(new NodeMap((javax.jcr.Node)node, nodeTypes)));

            if (!nodeTypes.isEmpty()) {
                return nodes.stream().filter(nodeMap -> {
                    NodeTypesPredicate nodeTypesPredicate = new NodeTypesPredicate(nodeTypes, false);
                    return nodeTypesPredicate.evaluateTyped(nodeMap.getJCRNode());
                }).collect(Collectors.toList());
            }
            return nodes;
        };

        return Optional.of(dataFetcher);
    }
    
}
