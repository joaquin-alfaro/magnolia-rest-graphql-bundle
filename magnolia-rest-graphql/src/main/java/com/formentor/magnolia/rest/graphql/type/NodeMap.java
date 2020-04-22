package com.formentor.magnolia.rest.graphql.type;

import info.magnolia.jcr.util.ContentMap;
import info.magnolia.rest.delivery.jcr.filter.NodeTypesPredicate;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class NodeMap extends ContentMap {
    // The get() of CHILDREN_FIELD will return the children nodes
    static private final String CHILDREN_FIELD = "children";

    // The children nodes will be filtered with NodeTypes defined at the fieldDefinition.
    private final List<String> nodeTypes;

    public NodeMap(Node content, List<String> nodeTypes) {
        super(content);
        this.nodeTypes = nodeTypes;
    }
    @Override
    public Object get(Object key) {
        String keyStr;
        try {
            keyStr = (String) key;
        } catch (ClassCastException e) {
            throw new ClassCastException("ContentMap accepts only String as a parameters, provided object was of type "
                    + (key == null ? "null" : key.getClass().getName()));
        }

        // Special case for field "children
        if (CHILDREN_FIELD.equals(keyStr)) {
            Node node = getJCRNode();
            try {
                return getChildren(node);
            } catch (RepositoryException e) {
                log.error("Errors getting children of node {}", node, e);
            }
        }
        return super.get(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return super.containsValue(value);
    }

    private List<NodeMap> getChildren(Node node) throws RepositoryException {
        List<NodeMap> children = new ArrayList<>();
        node.getNodes().forEachRemaining(child -> children.add(new NodeMap((Node)child, nodeTypes)));
        // Filter the children by nodeType
        if (!nodeTypes.isEmpty()) {
            return children.stream()
                    .filter(nodeMap -> {
                        NodeTypesPredicate nodeTypesPredicate = new NodeTypesPredicate(nodeTypes, false);
                        return nodeTypesPredicate.evaluateTyped(nodeMap.getJCRNode());
                    })
                    .collect(Collectors.toList());
        }

        return children;
    }
}
