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

public class LocalSPARQLExecution extends SPARQLEndpointExecution {

    private Model model;
    private String serviceId;


    public LocalSPARQLExecution(Query query, Set<String> inputSubset, Set<String> markers, SPARQLEndpointService sparqlEndpointService, HGQLSchema schema , Model localmodel, String rootType) {
        super(query, inputSubset, markers, sparqlEndpointService, schema, rootType);
        this.model = localmodel;
        this.serviceId = sparqlEndpointService.getId();
    }

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