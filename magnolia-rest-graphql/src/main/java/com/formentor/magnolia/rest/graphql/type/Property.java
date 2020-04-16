package com.formentor.magnolia.rest.graphql.type;

import lombok.extern.slf4j.Slf4j;

import javax.jcr.RepositoryException;

@Slf4j
public class Property {
    private final javax.jcr.Property property;

    public Property(javax.jcr.Property property) {
        this.property = property;
    }

    public String getName() throws RepositoryException {
        return property.getName();
    }

    public String getString() {
        try {
            return property.getString();
        } catch (Exception e) {
            log.error("Errors getting string from property {}", property, e);
            return null;
        }

    }

}
