package org.hypergraphql.datafetching.services.resultmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ObjectResult extends Result<Map<String, Object>> {

    private final static Logger LOGGER = LoggerFactory.getLogger(ObjectResult.class);
    //ToDo: Test what is better TreeMap or HashMap.
    Map<String, Map<String, Result>> subfields = new HashMap<>();   // subfields for each queried entity, first string is the ID of the object second String indicates the subfield.

    public ObjectResult(String name) {
        super(name);
    }

    public ObjectResult(String name, String alias) {
        super(name, alias);
    }

    public ObjectResult(String name, Map<String, Object> args) {
        super(name, args);
    }

    public ObjectResult(String name, String alias, Map<String, Object> args) {
        super(name, alias, args);
    }

    public Map<String, Result> getSubfiedldsOfObject(String iri){
        if(this.subfields.containsKey(iri)){
            return this.subfields.get(iri);
        }else{
            return null;
        }
    }

    public void addObject(String iri){
        if(this.subfields.containsKey(iri)){
            // Already in the list -> do nothing
        }else{
            this.subfields.put(iri, new HashMap<>());
        }
    }

    public void addObject(String iri, Map<String, Result> subfields){
        if(this.subfields.containsKey(iri)){
            final Map<String, Result> obj = this.subfields.get(iri);
            for(Map.Entry<String, Result> entry : subfields.entrySet()){
                if(obj.containsKey(entry.getKey())){
                    obj.get(entry.getKey()).merge(entry.getValue());
                }else {
                    obj.put(entry.getKey(), entry.getValue());
                }
            }

        }else {
            this.subfields.put(iri, subfields);
        }
    }

    @Override
    public Map<String, Object> generateJSON() {
        List<Object> subfields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        String fieldName = this.alias == null ? this.name : this.alias;
        if(!isList()){
            if(this.subfields.size()<=1){
                if(this.subfields.isEmpty()){
                    return null;
                }else{
                    final Map<String, Object> value = new HashMap<>();
                    this.subfields.entrySet().iterator().next().getValue().forEach((s, result) -> {
                        value.put(s, result.generateJSON());
                        this.errors += result.errors;
                    });
                    field.put(fieldName,value);
                }
            }else{
                this.errors += "Schema Error for "+ this.name + ": Only one result should exist, all queried values are returned in a list.";
            }
        }
        if(this.args != null && this.args.get(SPARQLServiceConverter.ORDER) != null){
            Comparator comparator = null;
            // If new order features are added extend the comparator cases here
            switch(this.args.get("order").toString()){
                case SPARQLServiceConverter.ORDER_ASC: comparator = Map.Entry.comparingByKey(); break;
                case SPARQLServiceConverter.ORDER_DESC: comparator = Map.Entry.comparingByKey(Comparator.reverseOrder()); break;
                default:
                    throw new IllegalStateException("Unexpected value: " + this.args.get("order"));
            }
            if (comparator != null) {
                Map<String, Map<String, Result>> subfields_sorted = new HashMap<>();
                this.subfields.entrySet().stream()
                        .sorted(comparator)
                        .forEachOrdered(o -> subfields_sorted.put(((Map.Entry<String, Map<String, Result>>) o).getKey(), ((Map.Entry<String, Map<String, Result>>) o).getValue()));
                this.subfields = subfields_sorted;
            }
        }
        subfields = this.subfields.values().stream()
//                .skip(args == null || args.get("offset") == null ? 0 : (long)args.get("offset"))   // offset interferes with multiple services limiter. offset is therefore only applied on the SPARQL queries and not on the final result build up
                .limit(args == null || args.get("limit") == null ? this.subfields.size() : (long)args.get("limit"))
                .map(objectEntry -> {
                    Map<String,Object> object = new HashMap<>();
                    for (Map.Entry<String, Result> entry : objectEntry.entrySet()) {
                        String key = entry.getKey();
                        Result values = entry.getValue();
                        String name = values.alias == null ? key : values.alias;
                        if(values instanceof ObjectResult){
                            object.put(name,((ObjectResult) values).generateJSON().get(name));
                        }else{
                            object.put(name, values.generateJSON());
                        }
                        this.errors += values.errors;
                    }
                    return object;
                })
                .collect(Collectors.toList());
        field.put(fieldName, subfields);
        return field;
    }

    @Override
    public void merge(Result result) {
        if(this.name.equals(result.name) && result instanceof ObjectResult){
            for(String entry : ((ObjectResult) result).subfields.keySet()){
                if(this.subfields.containsKey(entry)){
                    // Merge values
                    Map<String, Result> subResult = this.subfields.get(entry);
                    for(Map.Entry<String, Result> fieldEntry : ((ObjectResult) result).subfields.get(entry).entrySet()){
                        String key = fieldEntry.getKey();
                        Result values = fieldEntry.getValue();
                        if(subResult.containsKey(key)){
                            // object  result share same field -> merge
                            subResult.get(key).merge(values);
                        }else{
                            // field does is currently not defined for this object  -> just add
                            subResult.put(key, values);
                        }
                    }
                }else{
                    // entry is new just add
                    this.subfields.put(entry, ((ObjectResult) result).subfields.get(entry));
                }
            }
        }
    }

    public void deepSubfieldMerge(Result subfields){
        if(nodeId.equals(subfields.getNodeId())){
            // same level merge results on matching ids
            if(subfields instanceof ObjectResult){
                for(Map.Entry<String,Map<String,Result>> x : this.subfields.entrySet()){
                    if(((ObjectResult) subfields).subfields.containsKey(x.getKey())){
                        final Map<String, Result> resultMap = this.subfields.get(x.getKey());
                        for(Map.Entry<String,Result> y :  ((ObjectResult) subfields).subfields.get(x.getKey()).entrySet()){
                            if(resultMap.containsKey(y.getKey())){
                                resultMap.get(y.getKey()).merge(y.getValue());
                            }else{
                                resultMap.put(y.getKey(), y.getValue());
                            }
                        }
                    }
                }
            }else{
                LOGGER.error("");
            }
        }else{
            // different level forward to deeper level
            for (Map<String, Result> stringResultMap : this.subfields.values()) {
                if (stringResultMap.containsKey(subfields.name)) {
                    Result res = stringResultMap.get(subfields.name);
                    if(res instanceof ObjectResult){
                        ((ObjectResult) res).deepSubfieldMerge(subfields);
                    }else {
                        res.merge(subfields);
                    }
                }
            }
        }

//        for (Map<String, Result> stringResultMap : this.subfields.values()) {
//            if (stringResultMap.containsKey(subfields.name)) {
//                stringResultMap.get(subfields.name).merge(subfields);
//            }
//        }
    }



    public static void main(String[] args) {
        ObjectResult res = new ObjectResult("ex_Person");
        StringResult name = new StringResult("ex_name");
        name.values.add("Bob");
        name.values.add("Alice");
        StringResult age = new StringResult("ex_age");
        age.values.add("42");
        StringResult street = new StringResult("ex_street");
        street.values.add("Evergreen Terrace 742");
        Map<String, Result> addr_fields = new HashMap<>();
        addr_fields.put("ex_street", street);
        addr_fields.put("number", age);
        ObjectResult addr = new ObjectResult("ex_address");
        addr.subfields.put("http://example.org/addr_a", addr_fields);
        Map<String, Result> fields = new HashMap<String, Result>();
        fields.put("ex_name", name);
        fields.put("ex_age", age);
        fields.put("ex_address", addr);
        res.subfields.put("http://example.org/bob", fields);

        System.out.println(res.generateJSON());

        // Test Merge

        ObjectResult res2 = new ObjectResult("ex_Person");
        StringResult name2 = new StringResult("ex_name");
        name2.values.add("Eve");
        name2.values.add("Eve");
        StringResult age2 = new StringResult("ex_age");
        age2.values.add("24");
        StringResult street2 = new StringResult("ex_street");
        street2.values.add("Evergreen Terrace 666");
        Map<String, Result> addr_fields2 = new HashMap<String, Result>();
        addr_fields2.put("ex_street", street2);
        addr_fields2.put("number", age2);
        ObjectResult addr2 = new ObjectResult("ex_address");
        addr2.subfields.put("http://example.org/addr_a", addr_fields2);
        Map<String, Result> fields2 = new HashMap<String, Result>();
        fields2.put("ex_name", name2);
        fields2.put("ex_age", age2);
        fields2.put("ex_address", addr2);
        res2.subfields.put("http://example.org/bob", fields2);

        res.merge(res2);
        System.out.println(res.generateJSON());

        // Test ordering
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(SPARQLServiceConverter.ORDER,SPARQLServiceConverter.ORDER_DESC);

        name.args = arguments;
        System.out.println(res.generateJSON());

        // Test limit and offset

        arguments.put(SPARQLServiceConverter.LIMIT,1);
        arguments.put(SPARQLServiceConverter.OFFSET, 1);
        name.args = arguments;
//        long start = System.currentTimeMillis();
        System.out.println(res.generateJSON());
        long finish = System.currentTimeMillis();
//        long timeElapsed = finish - start;
//        System.out.println(timeElapsed);
    }
}