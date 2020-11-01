package org.hypergraphql.schemaextraction;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.web.HttpOp;
import org.apache.jena.update.UpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * A SPARQLExtraction  obtains the mapping configuration and the extraction query and is then able to extract the RDF
 * schema form given SPARQL endpoints or local RDf files. The extracted RDF schema is as good as the provided query.
 * The query can be a template as described in the documentation.
 */
public class SPARQLExtraction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SPARQLExtraction.class);
    private MappingConfig mapConfig;
    private QueryTemplatingEngine engine;
    private static final String RDF_FILE_ENDPOINT_ADDRESS = "http://localhost:";
    private static final String RDF_FILE_ENDPOINT_DATASET = "/dataset";

    /**
     * THe SPARQLExtraction objects must be instantiated with the mapping configuration and the query template inorder
     * to provide the methods to extract the schema from different services.
     * @param mapConfig configuration of the mapping
     * @param query_template SPARQL query to extract the schema. This query can be a template as described in the
     *                       documentation.
     */
    public SPARQLExtraction(MappingConfig mapConfig, String query_template){
        this.mapConfig = mapConfig;
        this.engine = new QueryTemplatingEngine(query_template,mapConfig);
    }


    /**
     * Extract the RDF schema from a given SPARQL endpoint. If the endpoint needs http authentication then username and
     * password must be given otherwise the can be null.
     * @param service URL of the SPARQL service endpoint
     * @param username Username to authenticate or null if no authentication needed
     * @param password Password to authenticate or null if no authentication needed
     * @return Returns a Model containing the RDF schema of the given SPARQL endpoint
     */
    public Model extractSchema(String service, String username, String password){
        return extractSchema(service, username, password, null);
    }

    /**
     * Extract the RDF schema from a given SPARQL endpoint. If the endpoint needs http authentication then username and
     * password must be given otherwise the can be null.
     * @param service URL of the SPARQL service endpoint
     * @param username Username to authenticate or null if no authentication needed
     * @param password Password to authenticate or null if no authentication needed
     * @param graph Graph were the data is stored, if no graph is defined in the config use null
     * @return Returns a Model containing the RDF schema of the given SPARQL endpoint
     */
    public Model extractSchema(String service, String username, String password, String graph){
        ARQ.init();
        if(username == null){
            username = "";
        }
        if(password == null){
            password = "";
        }
        Model upModel = ModelFactory.createDefaultModel();
        //Auth
        if(!username.equals("") || !password.equals("")){
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            Credentials credentials = new UsernamePasswordCredentials(username, password);
            credsProvider.setCredentials(AuthScope.ANY, credentials);
            HttpClient httpclient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
            HttpOp.setDefaultHttpClient(httpclient);
        }else{
            HttpOp.setDefaultHttpClient(HttpOp.initialDefaultHttpClient);
        }
        String query = engine.buildQuery(service, graph);
        UpdateAction.parseExecute(query, upModel);
//        Query queryFactory = QueryFactory.create(query) ;
//        QueryExecution qexec = QueryExecutionFactory.createServiceRequest(service, queryFactory) ;
//        Model resultModel = qexec.execConstruct() ;
//        qexec.close() ;
        return upModel;
    }

    /**
     * Extracts the RDF schema from the given RDF dataset file. To run the extraction query against the dataset a fuseki
     * server with the dataset is instantiated.
     * @param filename Name of the file that contains the rdf data
     * @param type Type of the file for example "TTL"
     * @return Returns a Model containing the RDF schema of the given dataset
     * @throws FileNotFoundException
     */
    public Model extractSchemaFromLocalRDFFile(String filename, String type) throws FileNotFoundException {
        return extractSchemaFromLocalRDFFile(filename, type, null);
    }

    /**
     * Extracts the RDF schema from the given RDF dataset file. To run the extraction query against the dataset a fuseki
     * server with the dataset is instantiated.
     * @param filename Name of the file that contains the rdf data
     * @param type Type of the file for example "TTL"
     * @param graph Graph were the data is stored, if no graph is defined in the config use null
     * @return Returns a Model containing the RDF schema of the given dataset
     * @throws FileNotFoundException
     */
    public Model extractSchemaFromLocalRDFFile(String filename, String type, String graph) throws FileNotFoundException {
        ARQ.init();
        FusekiServer server;
        Model model = ModelFactory.createDefaultModel();
        model.read(new FileInputStream(filename), null, type);

        LOGGER.info("Build up model from file {}", type);
        Dataset ds = DatasetFactory.create(model);
        server = FusekiServer.create()
                .add(RDF_FILE_ENDPOINT_DATASET, ds)
                .build();
        server.start();
        String address = RDF_FILE_ENDPOINT_ADDRESS+server.getPort()+RDF_FILE_ENDPOINT_DATASET;
        LOGGER.info("Start extracting schema from {} dataset", type);
        Model res = extractSchema(address,null, null, graph);
        server.stop();
        return res;
    }



    private Model fallbackExtraction(String service, String graph){
        String predicate_Query = "CONSTRUCT {\n" +
                "  ?predicate a ?property; ?domain ?pDomain; ?range ?pRange.\n" +
                "  ?pDomain a ?class.\n" +
                "  ?pRange a ?class.\n" +
                "}\n" +
                "?from\n" +
                "WHERE {\n" +
                "  {\n" +
                "    SELECT DISTINCT ?predicate ?pDomain ?pRange {\n" +
                "      {\n" +
                "        SELECT DISTINCT ?x ?pDomain ?pRange {\n" +
                "          ?s ?x ?o.\n" +
                "          OPTIONAL { ?s a ?pDomain}\n" +
                "          OPTIONAL { ?o a ?pRange}\n" +
                "        }\n" +
                "      }\n" +
                "      ?x (?subPropertiesOf|?equivalentProperties|^(?equivalentProperties)|?sameAses|^(?sameAses))* ?predicate\n" +
                "      FILTER(!isBlank(?predicate))\n" +
                "    }\n" +
                "  }"+
                "}";

        String query = new QueryTemplatingEngine(predicate_Query, this.mapConfig).buildQuery(service, graph);
        Query queryFactory = QueryFactory.create(query) ;
        QueryExecution qexec = QueryExecutionFactory.createServiceRequest(service, queryFactory) ;
        Model resultModel = qexec.execConstruct();
        qexec.close() ;

        String class_query_direct = "CONSTRUCT {\n" +
                "  ?y a rdfs:Class\n" +
                "}\n" +
                "WHERE {\n" +
                "  {\n" +
                "    SELECT DISTINCT ?y {\n" +
                "       [] a ?y FILTER(!isBlank(?y))\n" +
                "    }\n" +
                "  }\n" +
                "}";
        query = new QueryTemplatingEngine(predicate_Query, this.mapConfig).buildQuery(service, graph);
        queryFactory = QueryFactory.create(query) ;
        qexec = QueryExecutionFactory.createServiceRequest(service, queryFactory) ;
        resultModel.add(qexec.execConstruct());
        qexec.close() ;

        return resultModel;
    }
}
