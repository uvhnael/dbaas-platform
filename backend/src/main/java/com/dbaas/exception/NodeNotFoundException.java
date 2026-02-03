package com.dbaas.exception;

/**
 * Exception thrown when a node is not found.
 */
public class NodeNotFoundException extends RuntimeException {

    public NodeNotFoundException(String nodeId) {
        super("Node not found: " + nodeId);
    }

    public NodeNotFoundException(String nodeId, Throwable cause) {
        super("Node not found: " + nodeId, cause);
    }
}
