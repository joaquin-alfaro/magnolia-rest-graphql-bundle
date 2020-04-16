package com.formentor.magnolia.rest.graphql.type;

import info.magnolia.jcr.util.ContentMap;
import info.magnolia.rest.delivery.jcr.filter.NodeTypesPredicate;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class Node {
    private final javax.jcr.Node wrappedNode;
    // The children nodes will be filtered with NodeTypes defined at the fieldDefinition.
    private final List<String> nodeTypes;

    public Node(javax.jcr.Node node, List<String> nodeTypes) {
        this.wrappedNode = node;
        this.nodeTypes = nodeTypes;
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
        wrappedNode.getNodes().forEachRemaining(node -> children.add(new Node((javax.jcr.Node)node, nodeTypes)));
        // Filter the children by nodeType
        if (!nodeTypes.isEmpty()) {
            return children.stream()
                    .filter(node -> {
                        NodeTypesPredicate nodeTypesPredicate = new NodeTypesPredicate(nodeTypes, false);
                        return nodeTypesPredicate.evaluateTyped(node.getJCRNode());
                    })
                    .collect(Collectors.toList());
        }

        return children;
    }

    public javax.jcr.Node getJCRNode() {
        return wrappedNode;
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
