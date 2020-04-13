package com.formentor.magnolia.rest.graphql.type;

import javax.jcr.RepositoryException;

public class Property {
    private final javax.jcr.Property property;

    public Property(javax.jcr.Property property) {
        this.property = property;
    }

    public String getName() throws RepositoryException {
        return property.getName();
    }

    public String getString() throws RepositoryException {
        return property.getString();
    }

}
