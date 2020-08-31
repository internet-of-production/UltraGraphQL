package org.hypergraphql.datafetching;

import org.hypergraphql.datafetching.services.resultmodel.Result;

import java.util.Map;
import java.util.Set;

public class TreeExecutionResult {


//    private Model model;

    private Result formatedResult;
    private Map<String, Set<String>> resultSet;

//    public Model getModel() {
//        return model;
//    }

//    public void setModel(Model model) {
//        this.model = model;
//    }

    public Map<String, Set<String>> getResultSet() {
        return resultSet;
    }

    public void setResultSet(Map<String, Set<String>> resultSet) {
        this.resultSet = resultSet;
    }

    public void setFormatedResult(Result result) {
        this.formatedResult = result;
    }

    public Result getFormatedResult() {
        return formatedResult;
    }

}
