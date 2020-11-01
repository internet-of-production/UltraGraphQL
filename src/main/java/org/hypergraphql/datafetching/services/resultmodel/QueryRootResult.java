package org.hypergraphql.datafetching.services.resultmodel;

import java.util.HashMap;
import java.util.Map;

/**
 * The QueryRootResult stores the results of root queries. Root queries are the defined query fields of the GQL schema.
 * They are executed separately against the SPARQL endpoints but returned as one result.
 */
public class QueryRootResult extends Result<Map<String, Object>> {

    Map<String, Result> root_result;   // As key the nodeId (SPARQL variable is used to allow multiple queries with different selection sets without merging them)

    /**
     * Initialize Result object with a id and name
     *
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name   name of the query field
     */
    public QueryRootResult(String nodeId, String name) {
        super(nodeId, name);
        root_result = new HashMap<>();
    }

    /**
     * Generates a JSON representation of the content stored in the object. Result format depends on the type of the result.
     * If isList attribute is false but more than one entity is in the list than an error message is added to the error
     * attribute and the  output type of the result is changed to list to return all queried results.
     *
     * @return JSON value, exact format depends on the object itself.
     */
    @Override
    public Map<String, Object> generateJSON() {
        Map<String, Object> field = new HashMap<>();
        for(Map.Entry<String, Result> entry : this.root_result.entrySet()){
            String name = entry.getValue().alias == null ? entry.getValue().name : entry.getValue().alias;
            if(entry.getValue() instanceof ObjectResult){
                field.putAll(((ObjectResult)entry.getValue()).generateJSON());
                this.errors += entry.getValue().errors;
            }else{

            }

        }
        return field;
    }

    /**
     * Given result is merged into this result object if they are the result for the same queried field.
     *
     * @param result Result to be merged into this object.
     */
    @Override
    public void merge(Result result) {
        if(result instanceof QueryRootResult){
            // This case is not the intended way to use this method but to fail safe the result sets are merged
            for(Map.Entry<String, Result> entry : ((QueryRootResult) result).root_result.entrySet()){
                // add all results to the current result set through recursion
                this.merge(entry.getValue());
            }
        }else if(result instanceof ObjectResult || result instanceof StringResult){
            if(this.root_result.containsKey(result.nodeId)){
                this.root_result.get(result.nodeId).merge(result);
            }else{
                this.root_result.put(result.nodeId, result);
            }
        }
    }
}
