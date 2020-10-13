package org.hypergraphql.datafetching;

import org.hypergraphql.datafetching.services.resultmodel.Result;

import java.util.Map;
import java.util.Set;

/**
 * The class stores the results and IRIs for further queries of ONE executed query.
 * Contrary to this class the TreeExecutionResult class stores the union of multiple SPARQLExecutionResults on over the
 * same initial query.
 */
public class SPARQLExecutionResult {

   private  Map<String, Set<String>> resultSet;   // containing the IRIs for the queries one level deeper
   private Result result;   // containing the query results
//   private Model model;

    /**
     * Getter method for the resultSet of this object.
     * The resultSet contains the IRIs for the queries one level deeper.
     * Note: Used as input for the query generation of the queries underneath if present
     * @return Returns a Map with the field name as key and queried IRIs as values.
     */
    public Map<String, Set<String>> getResultSet() {
        return resultSet;
    }

//    public Model getModel() {
//        return model;
//    }

    /**
     * Getter method for the query results stored in this object.
     * @return Returns the query results.
     */
    public Result getResult(){
        return this.result;
    }

//    public void setModel(Model model) {
//        this.model = model;
//    }

    /**
     * Initializes a SPARQLExecutionResult with the provided parameters.
     * @param resultSet Map with the field name as key and queried IRIs as values.
     * @param result Query results
     */
    public SPARQLExecutionResult(Map<String, Set<String>> resultSet, Result result) {

        this.resultSet = resultSet;
        this.result = result;
//        this.model = model;
    }

    @Override
    public String toString() {

        return "RESULTS\n" +
                "Model : \n" + this.result.generateJSON() + "\n" +
                "ResultSet : \n" + this.resultSet.toString();
    }

//    public void close(){
//        this.model.close();
//    }
}
