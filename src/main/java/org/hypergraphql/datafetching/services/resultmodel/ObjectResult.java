package org.hypergraphql.datafetching.services.resultmodel;

import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ObjectResult represents query results of fields that have a type as output type (not Scalar value).
 * Each queried field with these conditions results in an own objet.
 * Nested fields in the query also result in nested ObjectResult objects.
 */
public class ObjectResult extends Result<Map<String, Object>> {

    private final static Logger LOGGER = LoggerFactory.getLogger(ObjectResult.class);
    Map<String, Map<String, Result>> subfields = new HashMap<>();   // subfields for each queried entity, first string is the ID of the object second String indicates the subfield.

    /**
     * Initalize ObjectResult with nodeId and name, both are mandatory for all ObjectResults
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the queried field
     */
    public ObjectResult(String nodeId, String name) {
        super(nodeId, name);
    }

    /**
     *  Additionally to the name the field also has a alias defined.
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     * @param alias Alias of the field name
     */
    public ObjectResult(String nodeId, String name, String alias) {
        super(nodeId, name, alias);
    }

    /**
     * Additionally arguments are defined for the field
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     * @param args Arguments of the field
     */
    public ObjectResult(String nodeId, String name, Map<String, Object> args) {
        super(nodeId, name, args);
    }

    /**
     * Additionally the field has an alias and arguments
     * @param nodeId Id of the query field also used as SPARQL variable
     * @param name Name of the field
     * @param alias Alias of the field
     * @param args Arguments of the field
     */
    public ObjectResult(String nodeId, String name, String alias, Map<String, Object> args) {
        super(nodeId, name, alias, args);
    }

    /**
     * Returns the queried subfields contained in this object for the given iri
     * @param iri IRI, the identifier of the object results
     * @return Returns the subfiedls and corresponding results for the given iri that are contained in this object
     */
    public Map<String, Result> getSubfiedldsOfObject(String iri){
        if(this.subfields.containsKey(iri)){
            return this.subfields.get(iri);
        }else{
            return null;
        }
    }

    /**
     * Adds a empty object entity to the object.
     * If this object already contains this entity nothing is done.
     * @param iri Id of the added object
     */
    public void addObject(String iri){
        if(this.subfields.containsKey(iri)){
            // Already in the list -> do nothing
        }else{
            this.subfields.put(iri, new HashMap<>());
        }
    }

    /**
     * Adds a object entity with the given subfields to this objet.
     * The given subfields are the results for the given iri.
     * If the given subfields or parts of the subfields already exist in this object, than the results are merged together.
     * Otherwise the subfields are are simply added.
     * @param iri IRI that identifies the given subfields
     * @param subfields Subfield results for the given IRI
     */
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
                        if(result instanceof ObjectResult){
                            value.put(s, ((ObjectResult)result).generateJSON().get(s));
                        }else{
                            value.put(s, result.generateJSON());
                        }
                        this.errors += result.errors;
                    });
                    field.put(fieldName,value);
                    return field;
                }
            }else{
                this.errors += "Schema Error for "+ this.name + ": Only one result should exist, all queried values are returned in a list.";
            }
        }
        try {
            if (this.args != null && this.args.get(SPARQLServiceConverter.ORDER) != null) {
                Comparator comparator = null;
                // If new order features are added extend the comparator cases here
                switch (this.args.get(SPARQLServiceConverter.ORDER).toString()) {
                    case SPARQLServiceConverter.ORDER_ASC:
                        comparator = Map.Entry.comparingByKey();
                        break;
                    case SPARQLServiceConverter.ORDER_DESC:
                        comparator = Map.Entry.comparingByKey(Comparator.reverseOrder());
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + this.args.get(SPARQLServiceConverter.ORDER));
                }
                if (comparator != null) {
                    Map<String, Map<String, Result>> subfields_sorted = new HashMap<>();
                    this.subfields.entrySet().stream()
                            .sorted(comparator)
                            .forEachOrdered(o -> subfields_sorted.put(((Map.Entry<String, Map<String, Result>>) o).getKey(), ((Map.Entry<String, Map<String, Result>>) o).getValue()));
                    this.subfields = subfields_sorted;
                }
            }
        }catch (ClassCastException e){
            this.errors += "Casting exception for the arguments of field " + this.name + ". ";
            LOGGER.error(e.getMessage());
            e.printStackTrace();
        }
        subfields = this.subfields.values().stream()
                .skip(args == null || args.get(SPARQLServiceConverter.OFFSET) == null ? 0 : ((Number) args.get(SPARQLServiceConverter.OFFSET)).longValue())   // offset interferes with multiple services limiter.
                .limit(args == null || args.get(SPARQLServiceConverter.LIMIT) == null ? this.subfields.size() : ((Number) args.get(SPARQLServiceConverter.LIMIT)).longValue())
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
        if(result == null){
            // Nothing needs to be merged
            return;
        }
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

    /**
     * Functions similarly to the merge method but checks the level of the result.
     * If the given subfield results are not on the same level as this object the subfield is forwarded one level deeper
     * to ensure that the results/subfields are merged correctly.
     * Furthermore this method only adds new data to result entities that already exist. Contrary to the merge method here,
     * only the existing entities are extended with further data but NO new entity is added.
     * This is due to the handling and execution of multiple queries where a query result may contain data for an entity
     * queried from another service but it also is possible that other data is also in the result for other entities.
     * The deepSubfieldMerge means here that the result entities from this object and below (result is tree structure)
     * only pick there data from the given subfield result to extend there data.
     * @param subfields Potential results for the subfields of this object
     */
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
    }


}