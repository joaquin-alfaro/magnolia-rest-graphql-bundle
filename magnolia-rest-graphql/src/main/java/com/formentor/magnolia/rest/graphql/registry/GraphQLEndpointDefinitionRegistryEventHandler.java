package com.formentor.magnolia.rest.graphql.registry;

import info.magnolia.event.EventHandler;

public interface GraphQLEndpointDefinitionRegistryEventHandler  extends EventHandler {
    /**
     * Called when a graphql definition has been added to the registry.
     */
    void onEndpointRegistered(GraphQLEndpointDefinitionRegistryEvent event);

    /**
     * Called when a graphql definition has been changed.
     */
    void onEndpointReregistered(GraphQLEndpointDefinitionRegistryEvent event);

    /**
     * Called when a graphql definition has been removed. The event contains only the name of the endpoint.
     */
    void onEndpointUnregistered(GraphQLEndpointDefinitionRegistryEvent event);

}
