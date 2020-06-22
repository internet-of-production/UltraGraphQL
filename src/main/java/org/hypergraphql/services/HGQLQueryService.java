package org.hypergraphql.services;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLSchema;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.datafetching.ExecutionForest;
import org.hypergraphql.datafetching.ExecutionForestFactory;
import org.hypergraphql.datamodel.ModelContainer;
import org.hypergraphql.query.QueryValidator;
import org.hypergraphql.query.ValidatedQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by szymon on 01/11/2017.
 */
public class HGQLQueryService {

    private GraphQL graphql;
    private GraphQLSchema schema;   // only used for IntrospectionQuery
    private HGQLSchema hgqlSchema;


    public HGQLQueryService(HGQLConfig config) {
        this.hgqlSchema = config.getHgqlSchema();
        this.schema = config.getSchema();

        this.graphql = GraphQL.newGraphQL(config.getSchema()).build();
    }

    public Map<String, Object> results(String query, String acceptType, ValidatedQuery validatedQuery) {
        Boolean isIntrospectionQuery = false;
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<Object, Object> extensions = new HashMap<>();
        List<GraphQLError> errors = new ArrayList<>();

        result.put("errors", errors);
        result.put("extensions", extensions);

        ExecutionInput executionInput;
        ExecutionResult qlResult = null;

        if (!validatedQuery.getValid()) {
            errors.addAll(validatedQuery.getErrors());
            return result;
        }
        final List<Definition> definitions = validatedQuery.getParsedQuery().getDefinitions();
        Definition def = definitions.get(0);
        if(def.getClass().isAssignableFrom(OperationDefinition.class)) {
            final OperationDefinition operationDefinition = (OperationDefinition)def;
            if(operationDefinition.getName() != null && operationDefinition.getName().equals("IntrospectionQuery")){

                isIntrospectionQuery = true;
            }
        }
        if (isIntrospectionQuery){

            qlResult = graphql.execute(query);   // data correctly returned
            data.putAll(qlResult.getData());

        } else {

            ExecutionForest queryExecutionForest =
                    new ExecutionForestFactory().getExecutionForest(validatedQuery.getParsedQuery(), hgqlSchema);

            ModelContainer client = new ModelContainer(queryExecutionForest.generateModel());

            if (acceptType == null) {
                executionInput = ExecutionInput.newExecutionInput()
                        .query(query)
                        .context(client)
                        .build();

                qlResult = graphql.execute(executionInput);

                data.putAll(qlResult.getData());
                data.put("@context", queryExecutionForest.getFullLdContext());
            } else {
                result.put("data", client.getDataOutput(acceptType));
            }
        }

        if (qlResult != null) {
            result.put("data", data);
            errors.addAll(qlResult.getErrors());
        }
        return result;
    }


}
