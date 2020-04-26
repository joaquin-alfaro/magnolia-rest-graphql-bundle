package com.formentor.magnolia.rest.graphql.service;

import com.formentor.magnolia.rest.graphql.GraphQLDefinition;
import com.formentor.magnolia.rest.graphql.registry.GraphQLEndpointDefinitionRegistry;
import com.formentor.magnolia.rest.graphql.registry.GraphQLEndpointDefinitionRegistryEvent;
import com.formentor.magnolia.rest.graphql.registry.GraphQLEndpointDefinitionRegistryEventHandler;
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
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.context.MgnlContext;
import info.magnolia.event.EventBus;
import info.magnolia.event.HandlerRegistration;
import info.magnolia.event.SystemEventBus;
import info.magnolia.rest.EndpointDefinition;
import info.magnolia.rest.delivery.jcr.QueryBuilder;
import info.magnolia.rest.delivery.jcr.filter.FilteringContentDecoratorBuilder;
import info.magnolia.rest.delivery.jcr.v2.JcrDeliveryEndpointDefinition;
import info.magnolia.rest.registry.EndpointDefinitionRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import java.io.File;
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
public class GraphQLProvider implements GraphQLEndpointDefinitionRegistryEventHandler {

    // Directive "definition"
    private static final String DIRECTIVE_definition = "definition";
    private static final String DIRECTIVE_delivery   = "delivery";
    private static final String DIRECTIVE_workspace  = "workspace";
    private static final String DIRECTIVE_rootPath   = "rootPath";
    private static final String DIRECTIVE_nodeTypes  = "nodeTypes";

    // Query Type name
    private static final String QUERY = "Query";

    // Query.nodes field
    private static final String QUERY_nodes           = "nodes";
    private static final String QUERY_nodes_workspace = "workspace";
    private static final String QUERY_nodes_path      = "path";
    private static final String NODE_type             = "Node";

    private final EndpointDefinitionRegistry endpointRegistry;
    private final GraphQLEndpointDefinitionRegistry graphQLRegistry;
    private final I18nContentSupport i18nContentSupport;
    private final EventBus systemEventBus;

    private GraphQL graphQL;
    private TypeDefinitionRegistry typeRegistry;
    private HandlerRegistration registerHandler;

    @Inject
    public GraphQLProvider(EndpointDefinitionRegistry endpointRegistry, GraphQLEndpointDefinitionRegistry graphQLRegistry, I18nContentSupport i18nContentSupport, @Named(SystemEventBus.NAME) EventBus systemEventBus) {
        this.endpointRegistry = endpointRegistry;
        this.graphQLRegistry = graphQLRegistry;
        this.i18nContentSupport = i18nContentSupport;
        this.systemEventBus = systemEventBus;
    }

    @PostConstruct
    private void init() {
        try {
            // Initialize graphQL
            typeRegistry = initTypeRegistry();
            RuntimeWiring runtimeWiring = buildWiring(typeRegistry);

            SchemaGenerator schemaGenerator = new SchemaGenerator();
            GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
            graphQL = GraphQL.newGraphQL(graphQLSchema).build();

            // Register all currently registered graphQL schemas
            for (DefinitionProvider<GraphQLDefinition> provider : graphQLRegistry.getAllProviders()) {
                try {
                    registerGraphQL(provider);
                } catch (Exception e) {
                    log.error("Failed to register endpoint [{}]", provider.getMetadata().getReferenceId(), e);
                    // Others should continue to be registered.
                }
            }

            // Register all currently registered "delivery endpoints"
            for (DefinitionProvider<EndpointDefinition> provider : endpointRegistry.getAllProviders()) {
                if (provider.get() instanceof JcrDeliveryEndpointDefinition) {
                    String sdl = buildGraphQLSDLForDeliveryEndpoint(provider);
                    addSchema(sdl);
                }
            }

            // Listen for changes to the registry to observe graphQL being added or removed
            // NOTE: It does not listen to delivery endpoints changes
            registerHandler = systemEventBus.addHandler(GraphQLEndpointDefinitionRegistryEvent.class, this);
        } catch (IOException e) {
            log.error("ERRORS during initialization of GraphQL");
        }
    }

    /**
     * Executes query
     *
     * @param query graphql query
     * @return
     */
    public Object execute(String query) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build();
        ExecutionResult executionResult = graphQL.execute(executionInput);

        return executionResult.toSpecification();
    }

    /**
     * Initialize the TypeRegistry with the sdl "schema.graphqls"
     * @return
     * @throws IOException
     */
    private TypeDefinitionRegistry initTypeRegistry() throws IOException {
        SchemaParser schemaParser = new SchemaParser();
        URL coreGraphql = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(coreGraphql, Charsets.UTF_8);
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(sdl);

        return typeRegistry;
    }

    /**
     * Builds wiring between Schema and Fetchers.
     *
     * @param typeRegistry
     * @return
     */
    private RuntimeWiring buildWiring(TypeDefinitionRegistry typeRegistry) {
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        Optional<TypeRuntimeWiring.Builder> queryType = buildQueryType(typeRegistry);
        if (queryType.isPresent()) {
            builder.type(queryType.get());
        }
        return builder.build();
    }

    /**
     * Register GraphQL definition
     * @param provider
     * @return
     */
    private Object registerGraphQL(DefinitionProvider<GraphQLDefinition> provider) {
        if (!provider.isValid()) {
            return null;
        }

        final GraphQLDefinition endpointDefinition = provider.get();
        final String sdl = endpointDefinition.getSdl();
        if (endpointDefinition.getSdl() != null) {
            addSchema(sdl);
        }

        return provider;
    }

    /**
     * Builds graphQL SDL for a given DeliveryEndpoint
     *
     * @param provider DefinitionProvider<JcrDeliveryEndpointDefinition>
     * @return
     */
    private String buildGraphQLSDLForDeliveryEndpoint(DefinitionProvider<EndpointDefinition> provider) {

        JcrDeliveryEndpointDefinition deliveryDefinition = (JcrDeliveryEndpointDefinition)provider.get();
        /**
         * 1. Get configuration of the Delivery endpoint required by the Field definition
         */
        String fieldName = provider.getMetadata().getReferenceId().replaceAll(File.separator, "_");
        String workspace = deliveryDefinition.getWorkspace();
        String rootPath = deliveryDefinition.getRootPath();
        List<String> nodeTypes = deliveryDefinition.getNodeTypes();

        /**
         * 2. Create schema SDL for the type
         */
        /*
             directive @delivery(workspace : String!, rootPath : String, nodeTypes : [String]) on FIELD_DEFINITION
             type Query {
                 nodes(workspace: String!, path: String): [Node]
                 pages : [Page] @definition(workspace: "website", nodeTypes: ["mgnl:page", "mgnl:area"])
                 tours_v1 : [Node] @delivery(workspace: "tours", rootPath: "/", nodeTypes: ["mgnl:content"])
             }
         */
        StringBuilder sdlBuilder = new StringBuilder();
        sdlBuilder.append("directive @delivery(workspace : String!, rootPath : String, nodeTypes : [String]) on FIELD_DEFINITION");
        sdlBuilder.append(" type Query {");
        sdlBuilder.append(fieldName).append(" : [Node]");
        sdlBuilder.append(" @delivery(workspace: ").append("\"" + workspace + "\"");
        if (rootPath != null) {
            sdlBuilder.append(", rootPath: ").append("\"" + rootPath + "\"");
        }
        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            String nodeTypesAsString = nodeTypes.stream()
                    .map(value -> "\"" + value + "\"")
                    .collect(Collectors.joining(","));
            sdlBuilder.append(", nodeTypes: [" + nodeTypesAsString + "])");
        }
        sdlBuilder.append("}");

        return sdlBuilder.toString();
    }

    /**
     * Builds wiring for "Query" type.
     *
     * Wiring for "nodes" field
     * Wiring for the rest of the fields. They must be tagged with the directive @definition
     *
     * @param typeRegistry
     * @return
     */
    private Optional<TypeRuntimeWiring.Builder> buildQueryType(TypeDefinitionRegistry typeRegistry) {
        Optional<TypeDefinition> typeDefinitionQuery = typeRegistry.getType(QUERY);
        if (!typeDefinitionQuery.isPresent()) {
            return Optional.empty();
        }
        ObjectTypeDefinition typeQuery = (ObjectTypeDefinition)typeDefinitionQuery.get();
        TypeRuntimeWiring.Builder builder = newTypeWiring(QUERY);

        /**
         * 1. Add implicit and always existing field "nodes"
         */
        builder.dataFetcher(QUERY_nodes, buildDataFetcherNodes());

        /**
         * 2. Add fetchers for fields tagged as @definition (Fields defined in GraphQL schema) or @delivery (Delivery endpoints)
         *
         * Tag @definition is used for declarative fields in graphql schema.
         * Tag @declarative is used for fields created at runtime for Delivery endpoint definitions.
         */
        List<FieldDefinition> fields = typeQuery.getFieldDefinitions();
        fields.stream()
                .filter(field -> !field.getName().equals(QUERY_nodes)) // The field "nodes" has been created below
                .filter(field -> (field.getDirective(DIRECTIVE_definition) != null || field.getDirective(DIRECTIVE_delivery) != null)) // Reject fields not tagged with Directive
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
            String workspace = dataFetchingEnvironment.getArgument(QUERY_nodes_workspace);
            String path = dataFetchingEnvironment.getArgument(QUERY_nodes_path);

            try {
                Session session = MgnlContext.getJCRSession(workspace);
                javax.jcr.Node rootNode = session.getNode((path == null) ?"/": path);
                List<Node> nodes = new ArrayList<>();
                rootNode.getNodes().forEachRemaining(node -> nodes.add(new Node((javax.jcr.Node)node, Collections.emptyList())));

                return nodes;
            } catch (Exception e) {
                return Collections.emptyList();
            }
        };
    }

    /**
     * Builds a fetcher for field tagged with @definition or @delivery directives
     *
     *      directive @definition(workspace : String!, rootPath : String, nodeTypes : String) on FIELD_DEFINITION
     *      directive @delivery(workspace : String!, rootPath : String, nodeTypes : String) on FIELD_DEFINITION
     *
     * @param field
     * @return
     */
    private Optional<DataFetcher> buildDataFetcherForField(FieldDefinition field) {
        /**
         * Get arguments of the directive
         */
        Directive definition = field.getDirective(DIRECTIVE_definition);
        if (definition == null) {
            definition = field.getDirective(DIRECTIVE_delivery);
        }
        if (definition == null) {
            return Optional.empty();
        }

        // Get workspace
        Argument argWorkspace = definition.getArgument(DIRECTIVE_workspace);
        if (argWorkspace == null) {
            return Optional.empty();
        }

        String workspace = ((StringValue)argWorkspace.getValue()).getValue();

        // Get rootPath
        Argument argRootPath = definition.getArgument(DIRECTIVE_rootPath);
        String rootPath = (argRootPath == null)? null: ((StringValue)argRootPath.getValue()).getValue();

        // Get list nodeTypes
        Argument argNodeTypes = definition.getArgument(DIRECTIVE_nodeTypes);
        final List<String> nodeTypes;
        if (argNodeTypes == null){
            nodeTypes = Collections.emptyList();
        } else {
            nodeTypes = ((ArrayValue)argNodeTypes.getValue()).getValues()
                    .stream()
                    .map(value -> ((StringValue)value).getValue())
                    .collect(Collectors.toList());
        }

        /**
         * Builds the DataFetcher
         */
        switch (definition.getName()) {
            case DIRECTIVE_definition:
                return Optional.of(buildDataFetcherForDefinition(workspace, rootPath, nodeTypes));
            case DIRECTIVE_delivery:
                return Optional.of(buildDataFetcherForDelivery(workspace, rootPath, nodeTypes));
            default: return Optional.empty();
        }
    }

    /**
     * Builds a DataFetcher for fields tagged with @definition
     *
     * I know it is similar to buildDataFetcherForDelivery but I choose the performance.
     *
     * @param workspace Workspace of the contents
     * @param rootPath  Path of the root node
     * @param nodeTypes List of node types used as a filter
     * @return
     */
    private DataFetcher buildDataFetcherForDefinition(String workspace, String rootPath, List<String> nodeTypes) {

        return dataFetchingEnvironment -> {
            Session session = MgnlContext.getJCRSession(workspace);

            Query query = QueryBuilder.inWorkspace(session.getWorkspace())
                    .rootPath(rootPath)
                    .nodeTypes(nodeTypes)
                    .build();
            QueryResult result = query.execute();
            NodeIterator nodeIterator = result.getNodes();

            FilteringContentDecoratorBuilder decorators = new FilteringContentDecoratorBuilder()
                    .childNodeTypes(Collections.emptyList())
                    .strict(false)
                    .depth(1)
                    .includeSystemProperties(true)
                    .supportI18n(i18nContentSupport);

            nodeIterator = decorators.wrapNodeIterator(nodeIterator);

            final List<NodeMap> nodes = new ArrayList<>();
            nodeIterator.forEachRemaining(node -> nodes.add(new NodeMap((javax.jcr.Node)node, nodeTypes)));

            return nodes;
        };
    }
    /**
     * Builds a DataFetcher for fields tagged with @delivery
     *
     * I know it is similar to buildDataFetcherForDelivery but I choose the performance.
     *
     * @param workspace Workspace of the contents
     * @param rootPath  Path of the root node
     * @param nodeTypes List of node types used as a filter
     * @return
     */
    private DataFetcher buildDataFetcherForDelivery(String workspace, String rootPath, List<String> nodeTypes) {

        return dataFetchingEnvironment -> {
            Session session = MgnlContext.getJCRSession(workspace);

            Query query = QueryBuilder.inWorkspace(session.getWorkspace())
                    .rootPath(rootPath)
                    .nodeTypes(nodeTypes)
                    .build();
            QueryResult result = query.execute();
            NodeIterator nodeIterator = result.getNodes();

            FilteringContentDecoratorBuilder decorators = new FilteringContentDecoratorBuilder()
                    .childNodeTypes(Collections.emptyList())
                    .strict(false)
                    .depth(1)
                    .includeSystemProperties(true)
                    .supportI18n(i18nContentSupport);

            nodeIterator = decorators.wrapNodeIterator(nodeIterator);
            final List<Node> nodes = new ArrayList<>();
            nodeIterator.forEachRemaining(node -> nodes.add(new Node((javax.jcr.Node)node, nodeTypes)));

            return nodes;
        };
    }

    /**
     * Add new schema file to current TypeRegistry
     *
     * Re-builds GraphQL
     * @param newsdl
     */
    private void addSchema(String newsdl) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry newtypeRegistry = schemaParser.parse(newsdl);
        mergeTypeRegistry(newtypeRegistry, typeRegistry);

        RuntimeWiring runtimeWiring = buildWiring(typeRegistry);
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
        graphQL = graphQL.transform(builder -> builder.schema(graphQLSchema));
    }

    /**
     * Merge TypeDefinitionRegistry "from" in "to"
     *
     * For existing Types adds the fields to the existing Type.
     * For non existing Types, adds it
     * @param from
     * @param to
     */
    private void mergeTypeRegistry(TypeDefinitionRegistry from, TypeDefinitionRegistry to) {
        for (ObjectTypeDefinition type: from.getTypes(ObjectTypeDefinition.class)) {
            mergeTypeInto(type, to);
        }
    }

    /**
     * Merge ObjectTypeDefinition inside TypeDefinitionRegistry
     *
     * If Type exists in TypeRegistry the adds the fields to the existing Type.
     * Otherwise add the Type to the TypeRegistry.
     *
     * @param newType
     * @param registry
     */
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

    @Override
    public void onEndpointRegistered(GraphQLEndpointDefinitionRegistryEvent event) {
        /**
         * TODO Register just the new schema
         */
        init();
    }

    @Override
    public void onEndpointReregistered(GraphQLEndpointDefinitionRegistryEvent event) {
        /**
         * TODO Update just the updated schema
         */
        init();
    }

    @Override
    public void onEndpointUnregistered(GraphQLEndpointDefinitionRegistryEvent event) {
        /**
         * TODO Unregister just the unregistered schema
         */
        init();
    }
}
