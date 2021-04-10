package org.hypergraphql.datafetching;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.jena.query.*;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.hypergraphql.datafetching.services.SPARQLEndpointService;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.hypergraphql.query.pattern.Query;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for the execution of SPARQL queries against local data models.
 * Intended use of this class is to execute and format the results of the given query.
 * The LocalModelSPARQLService uses multiple instances of this class to execute parts of queries in parallel.
 */
public class LocalSPARQLExecution extends SPARQLEndpointExecution {

    private Dataset model;   //ToDo: This attribute is irrelevant since it can be accessed over the SPARQLEndpointService attribute
    private String serviceId;


    /**
     *
     * @param query query or sub-query to be executed
     * @param inputSubset Possible IRIs of the parent query that are used to limit the results of this query/sub-query. Should be below the defined value limit (VALUES_SIZE_LIMIT)
     * @param markers variables for the SPARQL query
     * @param sparqlEndpointService Service object with data model, query is executed on this model
     * @param schema HGQLSchema the query is based on
     * @param localmodel Local data model the query will be executed on
     * @param rootType type of the query root
     */
    public LocalSPARQLExecution(Query query, Set<String> inputSubset, Set<String> markers, SPARQLEndpointService sparqlEndpointService, HGQLSchema schema , Dataset localmodel, String rootType) {
        super(query, inputSubset, markers, sparqlEndpointService, schema, rootType);
        this.model = localmodel;   //ToDo:
        this.serviceId = sparqlEndpointService.getId();
    }

    /**
     * Executes the query assigned to the object and builds-up the formatted result
     * @return Query results and IRIs for underlying queries
     */
    @Override
    public SPARQLExecutionResult call() {

        Map<String, Set<String>> resultSet = new HashMap<>();
        markers.forEach(marker -> resultSet.put(marker, new HashSet<>()));

        AtomicReference<Result> formatedResults = new AtomicReference<>();

        SPARQLServiceConverter converter = new SPARQLServiceConverter(schema);
        String sparqlQuery = converter.getSelectQuery(query, inputSubset, rootType, serviceId);
        LOGGER.debug("Service: {}; Query: {}", serviceId,sparqlQuery);
        org.apache.jena.query.Query jenaQuery = QueryFactory.create(sparqlQuery);

        QueryExecution qexec = QueryExecutionFactory.create(jenaQuery, model);
        ResultSet results = qexec.execSelect();
        results.forEachRemaining(solution -> {
            markers.stream()
                    .filter(solution::contains)
                    .forEach(marker -> resultSet.get(marker).add(solution.get(marker).asResource().getURI()));

            Result partialRes = this.sparqlEndpointService.getModelFromResults(query, solution, schema);
            if(formatedResults.get() == null){
                formatedResults.set(partialRes);
            }else{
                formatedResults.get().merge(partialRes);
            }
        });
        qexec.close();
        return new SPARQLExecutionResult(resultSet, formatedResults.get());
    }

}