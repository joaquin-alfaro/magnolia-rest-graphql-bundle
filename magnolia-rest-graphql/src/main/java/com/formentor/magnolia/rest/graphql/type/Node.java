package com.formentor.magnolia.rest.graphql.type;

import info.magnolia.jcr.util.ContentMap;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Node {
    private final javax.jcr.Node wrappedNode;

    public Node(javax.jcr.Node node) {
        this.wrappedNode = node;
    }

    public String getName() throws RepositoryException {
        return wrappedNode.getName();
    }

    public String getPath() throws RepositoryException {
        return wrappedNode.getPath();
    }

    public String getNodeType() throws RepositoryException {
        return wrappedNode.getPrimaryNodeType().getName();
    }

    public List<Node> getChildren() throws RepositoryException {
        List<Node> children = new ArrayList<>();
        wrappedNode.getNodes().forEachRemaining(node -> children.add(new Node((javax.jcr.Node)node)));
        return children;
    }

    public List<Property> getProperties() throws RepositoryException {
        List<Property> properties = new ArrayList<>();
        wrappedNode.getProperties().forEachRemaining(property -> properties.add(new Property((javax.jcr.Property)property)));
        return properties;
    }

    public ContentMap getContentMap() {
        return new ContentMap(wrappedNode);
    }
}
