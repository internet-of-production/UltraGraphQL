package org.hypergraphql.datafetching;

import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.datafetching.services.resultmodel.QueryRootResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ExecutionForest  {

    private final static Logger LOGGER = LoggerFactory.getLogger(ExecutionForest.class);
    private boolean isRoot;
    private HashSet<ExecutionTreeNode> forest;

    public ExecutionForest() {
        this.forest = new HashSet<>();
        this.isRoot = false;
    }

    public ExecutionForest(boolean isRoot) {
        this.forest = new HashSet<>();
        this.isRoot = isRoot;
    }

    public HashSet<ExecutionTreeNode> getForest() {
        return forest;
    }

    public Result generateModel() {

        ExecutorService executor = Executors.newFixedThreadPool(10);
//        Model model = ModelFactory.createDefaultModel();
        AtomicReference<Result> formatedResult = new AtomicReference<>();
        if(isRoot){
            formatedResult.set(new QueryRootResult(ExecutionTreeNode.ROOT_TYPE, ExecutionTreeNode.ROOT_TYPE));
        }
        Set<Future<Result>> futureModels = new HashSet<>();
        getForest().forEach(node -> {
            FetchingExecution fetchingExecution = new FetchingExecution(new HashSet<>(), node);
            futureModels.add(executor.submit(fetchingExecution));
        });
        futureModels.forEach(futureResult -> {
            try {
//                model.add(futureModel.get());
                if(formatedResult.get() == null){
                    formatedResult.set(futureResult.get());
                }else{
                    formatedResult.get().merge(futureResult.get());   //ToDo: This kind of merging does not apply correctly in the case of root queries
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Problem generating model", e);
            }
        });
        executor.shutdown();
        return formatedResult.get();
    }

    public String toString() {
        return this.toString(0);
    }

    public String toString(int i) {

        StringBuilder result = new StringBuilder();
        getForest().forEach(node -> result.append(node.toString(i)));
        return result.toString();
    }

    public Map<String, String> getFullLdContext() {

        Map<String, String> result = new HashMap<>();
        getForest().forEach(child -> result.putAll(child.getFullLdContext()));
        return result;
    }

}
