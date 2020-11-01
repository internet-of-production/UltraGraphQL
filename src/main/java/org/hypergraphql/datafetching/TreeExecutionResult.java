package org.hypergraphql.datafetching;

import org.hypergraphql.datafetching.services.resultmodel.Result;

import java.util.Map;
import java.util.Set;

/**
 * The class stores the results and IRIs for further queries of a set of queries executed queries (queries for the same
 * content but executed against different services or with different IRIs as input values)
 */
public class TreeExecutionResult {

//ToDo: Create Constructor and replace current instantiations of this class with the new Constructor. Current instantiations always use both attributes and can therefore be provided at creation.

//    private Model model;

    private Result formatedResult;   // containing the query results
    private Map<String, Set<String>> resultSet;   // containing the IRIs for the queries one level deeper

//    public Model getModel() {
//        return model;
//    }

//    public void setModel(Model model) {
//        this.model = model;
//    }

    /**
     * Getter method for the resultSet of this object.
     * The resultSet contains the IRIs for the queries one level deeper.
     * Note: Used as input for the query generation of the queries underneath if present
     * @return Returns a Map with the field name as key and queried IRIs as values.
     */
    public Map<String, Set<String>> getResultSet() {
        return resultSet;
    }

    /**
     * Setter method for the resultSet attribute
     * @param resultSet
     */
    public void setResultSet(Map<String, Set<String>> resultSet) {
        this.resultSet = resultSet;
    }

    /**
     * Setter method for the formattedResult attribute
     * @param result
     */
    public void setFormatedResult(Result result) {
        this.formatedResult = result;
    }

    /**
     * Getter method for the query results stored in this object.
     * @return Returns the query results.
     */
    public Result getFormatedResult() {
        return formatedResult;
    }

}
