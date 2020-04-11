package com.formentor.magnolia.rest.graphql.type;

import graphql.schema.DataFetcher;

import java.util.Map;

public interface GraphQLType {

    String getName();
    Map<String, DataFetcher> getFields();
}
