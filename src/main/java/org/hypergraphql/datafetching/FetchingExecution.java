package org.hypergraphql.datafetching;

import org.hypergraphql.datafetching.services.resultmodel.Result;

import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Wrapper Class for ExecutionTreeNode to make the class a callable class.
 * A call forwards to the generateTreeModel method.
 */
public class FetchingExecution implements Callable<Result> {

    private Set<String> inputValues;
    private ExecutionTreeNode node;

    public FetchingExecution(Set<String> inputValues, ExecutionTreeNode node) {

        this.inputValues = inputValues;
        this.node = node;
    }
    
    @Override
    public Result call() {
        return node.generateTreeModel(inputValues);
    }
}
