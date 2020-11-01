package org.hypergraphql.datafetching.services.resultmodel;

import java.util.HashMap;
import java.util.Map;

/**
 *  The Result class defines attributes and methods a result object needs to be able to insert, merge and finally
 *  generate the JSON result. The content of the result object correspond to the results of a queried field.
 * @param <T> Output type of the JSON object this object generates
 */
public abstract class Result <T>{

    String nodeId = null;   // unique id generated during the query translation for each field in the query
    String name = null;   // name of the query field
    String alias;
    Map<String, Object> args;  // arguments defined for the query field
    boolean isList = false;
    String errors = "";   // Contains the errors that occur during the result transformation   //ToDo: Change to List<GraphQLError>

    /**
     * Initialize Result object with a id and name
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name name of the query field
     */
    public Result(String nodeId, String name) {
        this.nodeId = nodeId;
        this.name = name;
        this.alias = null;
        this.args = new HashMap<>();
    }

    /**
     * Initialize Result object with a id, name and alias
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name name of the query field
     * @param alias Alias of the field
     */
    public Result(String nodeId, String name, String alias) {
        this.nodeId = nodeId;
        this.name = name;
        this.alias = alias;
        this.args = new HashMap<>();
    }

    /**
     * Initialize Result object with a id, name and defined arguments
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the query field
     * @param args Arguments of the field
     */
    public Result(String nodeId, String name, Map<String, Object> args) {
        this.nodeId = nodeId;
        this.name = name;
        this.alias = null;
        this.args = args;
    }

    /**
     * Initialize Result object with a id, name, alias and defined arguments
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name name of the query field
     * @param alias Alias of the field
     * @param args Arguments of the field
     */
    public Result(String nodeId, String name, String alias, Map<String, Object> args) {
        this.nodeId = nodeId;
        this.name = name;
        this.alias = alias;
        this.args = args;
    }

    /**
     * Getter method returning the errors that occured during the result transformation
     * @return Errors that occured during result transformation
     */
    public String getErrors() {
        return errors;
    }

    /**
     * Getter method to check whether the field output type is a list.
     * @return True if the result is a list, otherwise false
     */
    public Boolean isList() {
        return isList;
    }

    /**
     * Setter method to define whether the field output type is a list or not.
     * @param list True if the result of this field is a list, otherwise false
     */
    public void isList(Boolean list) {
        isList = list;
    }

    /**
     * Getter method for the nodeId of this Result object. The nodeId corresponds to a field of the query and is unique
     * for the the query. The nodeId is also used as SPARQL variable in the SPARQL quries.
     * @return
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Setter method to set the nodeId of this Result object
     * @param nodeId
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Generates a JSON representation of the content stored in the object. Result format depends on the type of the result.
     * If isList attribute is false but more than one entity is in the list than an error message is added to the error
     * attribute and the  output type of the result is changed to list to return all queried results.
     * @return JSON value, exact format depends on the object itself.
     */
    public abstract T generateJSON();

    /**
     * Given result is merged into this result object if they are the result for the same queried field.
     * @param result Result to be merged into this object.
     */
    public abstract void merge(Result result);

}




