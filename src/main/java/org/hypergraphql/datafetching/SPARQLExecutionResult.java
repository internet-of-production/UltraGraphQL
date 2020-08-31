package org.hypergraphql.datafetching;

import org.hypergraphql.datafetching.services.resultmodel.Result;

import java.util.Map;
import java.util.Set;

public class SPARQLExecutionResult {

   private  Map<String, Set<String>> resultSet;
   private Result result;
//   private Model model;

    public Map<String, Set<String>> getResultSet() {
        return resultSet;
    }

//    public Model getModel() {
//        return model;
//    }
    public Result getResult(){
        return this.result;
    }

//    public void setModel(Model model) {
//        this.model = model;
//    }

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
