package com.formentor.magnolia.rest.graphql.type;

import info.magnolia.jcr.util.ContentMap;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NodeMap extends ContentMap {
    public NodeMap(Node content) {
        super(content);
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
        if ("children".equals(keyStr)) {
            Node node = getJCRNode();
            try {
                List<NodeMap> children = new ArrayList<>();
                node.getNodes().forEachRemaining(child -> children.add(new NodeMap((Node)child)));
                return children;
            } catch (RepositoryException e) {
                log.error("Errors getting children of node {}", node, e);
            }
        }

        return super.get(key);
    }

    @Override
    public boolean containsValue(Object arg0) {
        return super.containsValue(arg0);
    }
}
