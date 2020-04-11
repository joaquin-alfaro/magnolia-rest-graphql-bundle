package com.formentor.magnolia.rest.graphql.type;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import info.magnolia.demo.travel.tours.service.TourServices;

import javax.inject.Inject;
import java.util.Map;

public class Query implements GraphQLType {

    private Map<String, DataFetcher> fields;

    @Inject
    public Query(TourServices tourServices) {

        fields = ImmutableMap.of(
                "toursByCategory", dataFetchingEnvironment -> {
                        String category = dataFetchingEnvironment.getArgument("category");
                        return tourServices.getToursByCategory("tourTypes", category);
                    }
        );
    }

    @Override
    public String getName() {
        return "Query";
    }

    @Override
    public Map<String, DataFetcher> getFields() {
        return fields;
    }
}
