package org.hypergraphql.schemaextraction;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.web.HttpOp;
import org.apache.jena.update.UpdateAction;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SPARQLExtraction {

    private MappingConfig mapConfig;
    private QueryTemplatingEngine engine;

    public SPARQLExtraction(MappingConfig mapConfig, String query_template){
        this.mapConfig = mapConfig;
        this.engine = new QueryTemplatingEngine(query_template,mapConfig);
    }

    public Model extractSchema(String service, String username, String password){
        if(username == null){
            username = "";
        }
        if(password == null){
            password = "";
        }
        Model upModel = ModelFactory.createDefaultModel();
        //Auth
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        credsProvider.setCredentials(AuthScope.ANY, credentials);
        HttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
        HttpOp.setDefaultHttpClient(httpclient);

        UpdateAction.parseExecute(engine.buildQuery(service), upModel);
        return upModel;
    }

}
