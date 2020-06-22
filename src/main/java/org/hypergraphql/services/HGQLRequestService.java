package org.hypergraphql.services;

import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.query.QueryValidator;
import org.hypergraphql.query.ValidatedQuery;

import java.util.*;

/**
 * Receives every GraphQL request and check if the request is an query or mutation.
 * The request is then forwarded to the corresponding Service
 */
public class HGQLRequestService {

    private final HGQLSchema hgqlSchema;
    private final GraphQLSchema schema;
    HGQLQueryService query_service;
    HGQLMutationService mutation_service;

    public HGQLRequestService(HGQLConfig config){
        this.hgqlSchema = config.getHgqlSchema();
        this.schema = config.getSchema();
        this.query_service = new HGQLQueryService(config);
        this.mutation_service = new HGQLMutationService(config);
    }

    /**
     * Validates the request and decides if the request is a mutation or query and forwards the request to the
     * corresponding handler.
     * @param request GraphQL request
     * @param acceptType
     * @return Request result
     */
    public Map<String, Object> results(String request, String acceptType) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<Object, Object> extensions = new HashMap<>();
        List<GraphQLError> errors = new ArrayList<>();

        result.put("errors", errors);
        result.put("extensions", extensions);

        ValidatedQuery validatedQuery = new QueryValidator(schema).validateQuery(request);
        if (!validatedQuery.getValid()) {
            errors.addAll(validatedQuery.getErrors());
            return result;
        }

        final List<Definition> definitions = validatedQuery.getParsedQuery().getDefinitions();
        Definition def = definitions.get(0);
        if(def.getClass().isAssignableFrom(OperationDefinition.class)) {
            final OperationDefinition operationDefinition = (OperationDefinition)def;
            if(operationDefinition.getOperation().name().equals(OperationDefinition.Operation.MUTATION.toString())){
                System.out.println("Mutation");
                final Map<String, Object> query_results = mutation_service.results(request, acceptType, validatedQuery);
                result.put("mutation", query_results.get("mutation"));
                errors.addAll((List<GraphQLError>)query_results.get("errors"));
                data.putAll((Map<? extends String, ?>) query_results.get("data"));
                result.put("data", data);
            }else if(operationDefinition.getOperation().name().equals(OperationDefinition.Operation.QUERY.toString())){
                final Map<String, Object> query_results = query_service.results(request, acceptType, validatedQuery);
                errors.addAll((List<GraphQLError>)query_results.get("errors"));
                data.putAll((Map<? extends String, ?>) query_results.get("data"));
                result.put("data", data);
            }
        }

        return result;
    }
}
