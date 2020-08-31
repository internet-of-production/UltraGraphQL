package org.hypergraphql.datafetching.services.resultmodel;

import com.fasterxml.jackson.databind.JsonNode;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class StringResult extends Result<Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(StringResult.class);

    Set<String> values = new HashSet<>();

    public StringResult(String name) {
        super(name);
    }

    public StringResult(String name, String alias) {
        super(name, alias);
    }

    public StringResult(String name, Map<String, Object> args) {
        super(name, args);
    }

    public StringResult(String name, String alias, Map<String, Object> args) {
        super(name, alias, args);
    }

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
            values = (List) values.stream()
                    .skip(args == null || args.get("offset") == null ? 0 : (long)args.get("offset"))  // Currently limit and offset not applied to Literal values therefoer applied here  -> offset interferes with multiple services limiter. offset is therefore only applied on the SPARQL queries and not on the final result build up
                    .limit(args == null || args.get("limit") == null ? this.values.size() : (long)args.get("limit"))
                    .collect(Collectors.toList());
        }
        return values;
    }

    @Override
    public void merge(Result result) {
        if(result instanceof  StringResult){
            if(!this.name.equals(result.name)){
                LOGGER.error("Merging fields with different names");
            }
            this.values.addAll(((StringResult) result).values);
        }else{
            LOGGER.error("Merging string results with objet results not possible");
        }
    }


}
