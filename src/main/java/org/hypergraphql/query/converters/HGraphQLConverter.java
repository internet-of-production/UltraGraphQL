package org.hypergraphql.query.converters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hypergraphql.config.schema.QueryFieldConfig;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.query.pattern.Query;
import org.hypergraphql.query.pattern.QueryPattern;
import org.hypergraphql.query.pattern.SubQueriesPattern;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_QUERY_GET_FIELD;

public class HGraphQLConverter {
    private HGQLSchema schema;

    public  HGraphQLConverter(HGQLSchema schema ) {

        this.schema = schema;
    }
    private String urisArgSTR(Set<String> uris) {

        final String QUOTE = "\"%s\"";
        final String ARG = "(uris:[%s])";

        Set<String> quotedUris = new HashSet<>();

        for (String  uri : uris) {
            quotedUris.add(String.format(QUOTE, uri));
        }

        String uriSequence = String.join(",", quotedUris);

        return String.format(ARG, uriSequence);
    }

    private String getArgsSTR(Map<String, Object> getArgs) {

        if (getArgs != null) {
            return "";
        }

//        final String LIM = "limit:%s ";
//        final String OFF = "offset:%s ";
        final String ARG = "(%s)";

        String argsStr = "";

//        if (getArgs.has("limit")) {
//            argsStr += String.format(LIM, getArgs.get("limit").asInt());
//        }
//        if (getArgs.has("offset")) {
//            argsStr += String.format(OFF, getArgs.get("offset").asInt());
//        }
        return String.format(ARG, argsStr);
    }

    private String langSTR(Map<String, Object> langArg) {

        if (langArg == null) {
            return "";
        }
        if(langArg.containsKey(SPARQLServiceConverter.LANG)){
            return "(lang:\"" + (String) langArg.get(SPARQLServiceConverter.LANG) + "\")" ;
        }
        return "";
    }

    private String querySTR(String content) {

        final String QUERY = "{ %s }";
        return String.format(QUERY, content);
    }


    public String convertToHGraphQL(Query jsonQuery, Set<String> input, String rootType) {

        Map<String, QueryFieldConfig> queryFields = schema.getQueryFields();
        boolean root = (!jsonQuery.isSubQuery() && queryFields.containsKey(((QueryPattern)jsonQuery).name));

        if (root) {
            if (queryFields.get(((QueryPattern)jsonQuery).name).type().equals(HGQL_QUERY_GET_FIELD)) {
                return getSelectRoot_GET((QueryPattern)jsonQuery);
            } else {
                return getSelectRoot_GET_BY_ID((QueryPattern)jsonQuery);
            }
        } else {
            return getSelectNonRoot((SubQueriesPattern) jsonQuery, input, rootType);
        }
    }

    private String getSelectRoot_GET_BY_ID(QueryPattern jsonQuery) {

        Set<String> uris = (Set<String>) jsonQuery.args.get(SPARQLServiceConverter.LANG);
        String key = jsonQuery.name + urisArgSTR(uris);
        String content = getSubQuery(jsonQuery.fields, jsonQuery.targetType);
        return querySTR(key + content);
    }


    private String getSelectRoot_GET(QueryPattern jsonQuery) {

        String key = jsonQuery.name + getArgsSTR(jsonQuery.args);
        String content = getSubQuery(jsonQuery.fields, jsonQuery.targetType);
        return querySTR(key + content);
    }

    private String getSelectNonRoot(SubQueriesPattern jsonQuery, Set<String> input, String rootType) {

        String topQueryFieldName = rootType + "_GET_BY_ID";
        String key = topQueryFieldName + urisArgSTR(input);
        String content = getSubQuery(jsonQuery, rootType);
        return querySTR(key + content);
    }

    private String getSubQuery(SubQueriesPattern fieldsJson, String parentType) {

        Set<String> subQueryStrings = new HashSet<>();

        if (schema.getTypes().containsKey(parentType)) {
            subQueryStrings.add("_id");
            subQueryStrings.add("_type");
        }

        if (fieldsJson==null || fieldsJson.subqueries == null) {
            if (subQueryStrings.isEmpty()) {
                return "";
            } else {
                return querySTR(String.join(" ", subQueryStrings));
            }
        } else {


            for(QueryPattern field : fieldsJson.subqueries) {
                SubQueriesPattern fieldsArray = field.fields;
                String arg = langSTR(field.args);
                String fieldString = field.name + arg + " " + getSubQuery(fieldsArray, field.targetType);
                subQueryStrings.add(fieldString);
            }
        }

        if (!subQueryStrings.isEmpty()) {
            return querySTR(String.join(" ", subQueryStrings));
        } else {
            return "";
        }
    }

}
