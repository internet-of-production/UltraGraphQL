package org.hypergraphql.datafetching.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpOp;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import org.apache.jena.update.UpdateAction;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.SPARQLEndpointExecution;
import org.hypergraphql.datafetching.SPARQLExecutionResult;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datamodel.HGQLSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SPARQLEndpointService extends SPARQLService {

    private final static Logger LOGGER = LoggerFactory.getLogger(SPARQLEndpointService.class);
    private String url;
    private String user;
    private String password;
    final static int VALUES_SIZE_LIMIT = 100;

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user == null? "" : user;
    }

    public String getPassword() {
        return password== null? "" : password;
    }

    @Override
    public TreeExecutionResult executeQuery(JsonNode query, Set<String> input, Set<String> markers , String rootType , HGQLSchema schema) {

        LOGGER.debug(String.format("%s: Start query execution", this.getId()));
        Map<String, Set<String>> resultSet = new HashMap<>();
        Model unionModel = ModelFactory.createDefaultModel();
        Set<Future<SPARQLExecutionResult>> futureSPARQLresults = new HashSet<>();

        List<String> inputList = getStrings(query, input, markers, rootType, schema, resultSet);

        // run the query but if the id restriction form _GET_BY_ID has more then VALUES_SIZE_LIMIT URIS then run multiple queries with each query having maximum of VALUES_SIZE_LIMIT IDds
        do {

            Set<String> inputSubset = new HashSet<>();
            if(!inputList.isEmpty()){
                int size = inputList.size()<VALUES_SIZE_LIMIT? inputList.size() : VALUES_SIZE_LIMIT;
                inputSubset = inputList.stream().limit(size).collect(Collectors.toSet());
                inputList = inputList.stream().skip(size).collect(Collectors.toList());
            }
            ExecutorService executor = Executors.newFixedThreadPool(50);
            SPARQLEndpointExecution execution = new SPARQLEndpointExecution(query,inputSubset,markers,this, schema, rootType);
            futureSPARQLresults.add(executor.submit(execution));

        } while (inputList.size()>0);

        iterateFutureResults(futureSPARQLresults, unionModel, resultSet);

        TreeExecutionResult treeExecutionResult = new TreeExecutionResult();
        treeExecutionResult.setResultSet(resultSet);
        treeExecutionResult.setModel(unionModel);

        return treeExecutionResult;
    }

    public Boolean executeUpdate(String update){
        try{
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            Credentials credentials =
                    new UsernamePasswordCredentials(this.getUser(), this.getPassword());
            credsProvider.setCredentials(AuthScope.ANY, credentials);
            HttpClient httpclient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
            HttpOp.setDefaultHttpClient(httpclient);

            HttpOp.execHttpPost(getUrl() + "/update", WebContent.contentTypeSPARQLUpdate, update, null, null);
            return true;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
    }

    void iterateFutureResults (
            final Set<Future<SPARQLExecutionResult>> futureSPARQLResults,
            final Model unionModel,
            Map<String, Set<String>> resultSet
    ) {

        for (Future<SPARQLExecutionResult> futureExecutionResult : futureSPARQLResults) {
            try {
                SPARQLExecutionResult result = futureExecutionResult.get();
                unionModel.add(result.getModel());
                result.getResultSet().forEach((var, uris) ->{
                    if(resultSet.get(var)== null){
                        resultSet.put(var, uris);
                    }else{
                        resultSet.get(var).addAll(uris);
                    }
                } );
//                resultSet.putAll(result.getResultSet());
            } catch (InterruptedException
                    | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Init resultSet by inserting each marker as key with a empty set as value. Also add to the input set the URIs of
     * the id argument of the query and return them as list.
     * @param query JSON representation of a graphql query needed to extract information about query arguments
     * @param input
     * @param markers variables for the SPARQL query
     * @param rootType type of the query root
     * @param schema HGQLSchema
     * @param resultSet
     * @return List with input values
     */
    List<String> getStrings(JsonNode query, Set<String> input, Set<String> markers, String rootType, HGQLSchema schema, Map<String, Set<String>> resultSet) {
        for (String marker : markers) {
            resultSet.put(marker, new HashSet<>());
        }
        //ToDo: Handle the _GET_BY_ID to function if the argument id is given and is not null
        if (rootType.equals("Query")&&schema.getQueryFields().get(query.get("name").asText()).type().equals(HGQLVocabulary.HGQL_QUERY_GET_BY_ID_FIELD)) {
            Iterator<JsonNode> uris = query.get("args").get("uris").elements();
            while (uris.hasNext()) {
                String uri = uris.next().asText();
                input.add(uri);
            }
        }
        return new ArrayList<>(input);
    }

    @Override
    public void setParameters(ServiceConfig serviceConfig) {

        super.setParameters(serviceConfig);

        this.id = serviceConfig.getId();
        this.url = serviceConfig.getUrl();
        this.user = serviceConfig.getUser();
        this.graph = serviceConfig.getGraph();
        this.password = serviceConfig.getPassword();

    }
}
