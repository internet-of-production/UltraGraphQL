package org.hypergraphql.datafetching;

import org.apache.jena.rdf.model.Model;

import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Wrapper Class for ExecutionTreeNode to make the class a callable class.
 * A call forwards to the generateTreeModel method.
 */
public class FetchingExecution implements Callable<Model> {

    private Set<String> inputValues;
    private ExecutionTreeNode node;

    public FetchingExecution(Set<String> inputValues, ExecutionTreeNode node) {

        this.inputValues = inputValues;
        this.node = node;
    }
    
    @Override
    public Model call() {
        return node.generateTreeModel(inputValues);
    }
}
