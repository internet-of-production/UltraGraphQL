package org.hypergraphql.datafetching.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpOp;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.ExecutionTreeNode;
import org.hypergraphql.datafetching.SPARQLEndpointExecution;
import org.hypergraphql.datafetching.SPARQLExecutionResult;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.hypergraphql.query.pattern.Query;
import org.hypergraphql.query.pattern.QueryPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    /**
     * Executes the given query against the SPARQL endpoint assigned to this object.
     * If the remote SPARQL endpoint needs authentication the configured username and password are used for a HTTP authentication.
     * If more IRIs are provided in input then defined in VALUES_SIZE_LIMIT as limit the values are distributed over multiple queries to sta below the limit.
     * @param query query or sub-query to be executed
     * @param input Possible IRIs of the parent query that are used to limit the results of this query/sub-query
     * @param markers variables for the SPARQL query
     * @param rootType type of the query root
     * @param schema HGQLSchema the query is based on
     * @return Query results and IRIs for underlying queries
     */
    @Override
    public TreeExecutionResult executeQuery(Query query, Set<String> input, Set<String> markers , String rootType , HGQLSchema schema) {

        LOGGER.debug(String.format("%s: Start query execution", this.getId()));
        Map<String, Set<String>> resultSet = new HashMap<>();
        Set<Future<SPARQLExecutionResult>> futureSPARQLresults = new HashSet<>();

        List<String> inputList = getStrings(query, input, markers, rootType, schema, resultSet);

        // run the query but if the id restriction form _GET_BY_ID has more then VALUES_SIZE_LIMIT URIS then run multiple queries with each query having maximum of VALUES_SIZE_LIMIT IDds
        List<ExecutorService> executors = new ArrayList<>();
        do {

            Set<String> inputSubset = new HashSet<>();
            if(!inputList.isEmpty()){
                int size = Math.min(inputList.size(), VALUES_SIZE_LIMIT);
                inputSubset = inputList.stream().limit(size).collect(Collectors.toSet());
                inputList = inputList.stream().skip(size).collect(Collectors.toList());
            }
            ExecutorService executor = Executors.newFixedThreadPool(50);
            executors.add(executor);
            SPARQLEndpointExecution execution = new SPARQLEndpointExecution(query,inputSubset,markers,this, schema, rootType);
            futureSPARQLresults.add(executor.submit(execution));

        } while (inputList.size()>0);

        Result result = iterateFutureResults(futureSPARQLresults, resultSet);

        TreeExecutionResult treeExecutionResult = new TreeExecutionResult();
        treeExecutionResult.setResultSet(resultSet);
        treeExecutionResult.setFormatedResult(result);
        executors.forEach(executorService -> executorService.shutdown());
        return treeExecutionResult;
    }

    /**
     * Executes the given SPARQL Update against the SPARQL endpoint assigned to this object.
     * If the remote SPARQL endpoint needs authentication the configured username and password are used for a HTTP authentication.
     * @param update SPARQL Update
     * @return True if the update succeeds otherwise False
     */
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

    Result iterateFutureResults (
            final Set<Future<SPARQLExecutionResult>> futureSPARQLResults,
            Map<String, Set<String>> resultSet
    ) {
        Result res = null;
        for (Future<SPARQLExecutionResult> futureExecutionResult : futureSPARQLResults) {
            try {
                SPARQLExecutionResult result = futureExecutionResult.get();
                if(res == null){
                    res = result.getResult();
                }else{
                    res.merge(result.getResult());
                }
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
        return res;
    }

    /**
     * Init resultSet by inserting each marker as key with a empty set as value. Also add to the input set the URIs of
     * the id argument of the query and return them as list.
     * @param query JSON representation of a graphql query needed to extract information about query arguments
     * @param input Possible IRIs of the parent query that are used to limit the results of this query/sub-query
     * @param markers variables for the SPARQL query
     * @param rootType type of the query root
     * @param schema HGQLSchema
     * @param resultSet A Map where the markers are inserted as keys with an empty set as value
     * @return List with input values
     */
    List<String> getStrings(Query query, Set<String> input, Set<String> markers, String rootType, HGQLSchema schema, Map<String, Set<String>> resultSet) {
        for (String marker : markers) {
            resultSet.put(marker, new HashSet<>());
        }
        //ToDo: Handle the _GET_BY_ID to function if the argument id is given and is not null
        if (rootType.equals(ExecutionTreeNode.ROOT_TYPE)&&schema.getQueryFields().get(((QueryPattern)query).name).type().equals(HGQLVocabulary.HGQL_QUERY_GET_BY_ID_FIELD)) {

            Set<String> ids = (Set<String>) ((QueryPattern)query).args.get(SPARQLServiceConverter.ID);
            ids.forEach(input::add);
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
