package org.hypergraphql.datafetching.services.resultmodel;

import com.fasterxml.jackson.databind.JsonNode;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  StringResult represents Literal results of a queried field.
 *  The StringResult object is the leaf object of the result tree.
 */
public class StringResult extends Result<Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(StringResult.class);


    Set<String> values = new HashSet<>();   // contains the literal values (results) of this field

    /**
     * Initalize ObjectResult with nodeId and name, both are mandatory for all StringResults
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     */
    public StringResult(String nodeId, String name) {
        super(nodeId, name);
    }

    /**
     *  Additionally to the name the field also has a alias defined.
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     * @param alias Alias of the field name
     */
    public StringResult(String nodeId, String name, String alias) {
        super(nodeId, name, alias);
    }

    /**
     * Additionally arguments are defined for the field
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     * @param args Arguments of the field
     */
    public StringResult(String nodeId, String name, Map<String, Object> args) {
        super(nodeId, name, args);
    }

    /**
     * Additionally the field has an alias and arguments
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     * @param alias Alias of the field
     * @param args Arguments of the field
     */
    public StringResult(String nodeId, String name, String alias, Map<String, Object> args) {
        super(nodeId, name, alias, args);
    }

    /**
     * Adds a String value to the result list of this object.
     * This method is not restricted by the isList attribute. Values are always added but during the generation of the
     * JSON representation an error message is added and the field output type is changed to list to return all queried results.
     * @param value
     */
    public void addString(String value){
        this.values.add(value);
    }

    @Override
    public Object generateJSON() {
        List values = new ArrayList(this.values);
        if(!isList()){
            if(values.size() <=1){
                if (values.isEmpty()) {
                    return null;
                }
                return values.get(0);
            }else{
                this.errors += "Schema Error for "+ this.name + ": Only one result should exist, all queried values are returned in a list.";
            }
        }
        if(this.args != null && this.args.get(SPARQLServiceConverter.ORDER) != null) {

            // sort results
            Comparator comparator = null;
            String order = (String) this.args.get(SPARQLServiceConverter.ORDER);   //ToDo: If new order features are implemented the type of order might change
            switch (order) {
                case SPARQLServiceConverter.ORDER_ASC:
                    comparator =Comparator.naturalOrder();
                    break;
                case SPARQLServiceConverter.ORDER_DESC:
                    comparator =Comparator.reverseOrder();
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + this.args.get("order"));
            }
            if(comparator != null){
                values = (List) values.stream()
                        .sorted(comparator)
                        .collect(Collectors.toList());
            }

            // apply limiters
            try {
                values = (List) values.stream()
                        .skip(args == null || args.get("offset") == null ? 0 : ((Number) args.get(SPARQLServiceConverter.OFFSET)).longValue())  // Currently limit and offset not applied to Literal values therefore applied here  -> offset interferes with multiple services limiter. offset is therefore only applied on the SPARQL queries and not on the final result build up
                        .limit(args == null || args.get("limit") == null ? this.values.size() : ((Number) args.get(SPARQLServiceConverter.LIMIT)).longValue())
                        .collect(Collectors.toList());
            }catch (ClassCastException e){
                this.errors += "Casting exception for the arguments of field " + this.name + ". ";
                LOGGER.error(e.getMessage());
                e.printStackTrace();
            }
        }
        return values;
    }

    @Override
    public void merge(Result result) {
        if(result instanceof  StringResult){
            if(!this.name.equals(result.name)){
                LOGGER.error("Tried to merge fields with different names");
            }else{
                this.values.addAll(((StringResult) result).values);
            }
        }else{
            LOGGER.error("Merging string results with objet results not possible");
        }
    }

    /**
     * Getter method to get the literal values stored in this object
     * @return Literal values of this stored object
     */
    Set<String> getValues() {
        return values;
    }

}
