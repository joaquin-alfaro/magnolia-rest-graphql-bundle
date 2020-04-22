package com.formentor.magnolia.rest.graphql.registry;

import com.formentor.magnolia.rest.graphql.GraphQLDefinition;
import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.event.Event;
import info.magnolia.rest.registry.EndpointDefinitionRegistryEventType;

public class GraphQLEndpointDefinitionRegistryEvent implements Event<GraphQLEndpointDefinitionRegistryEventHandler> {
    private final EndpointDefinitionRegistryEventType type;
    private final DefinitionProvider<GraphQLDefinition> graphQLDefinitionDefinitionProvider;

    public GraphQLEndpointDefinitionRegistryEvent(EndpointDefinitionRegistryEventType type, DefinitionProvider<GraphQLDefinition> graphQLDefinitionDefinitionProvider) {
        this.type = type;
        this.graphQLDefinitionDefinitionProvider = graphQLDefinitionDefinitionProvider;
    }

    public DefinitionProvider<GraphQLDefinition> getGraphQLDefinitionDefinitionProvider() {
        return graphQLDefinitionDefinitionProvider;
    }

    @Override
    public void dispatch(GraphQLEndpointDefinitionRegistryEventHandler handler) {
        switch(type) {
            case REGISTERED:
                handler.onEndpointRegistered(this);
                break;
            case REREGISTERED:
                handler.onEndpointReregistered(this);
                break;
            case UNREGISTERED:
                handler.onEndpointUnregistered(this);
                break;
        }
    }
}
