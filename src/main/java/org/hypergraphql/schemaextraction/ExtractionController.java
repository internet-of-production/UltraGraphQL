package org.hypergraphql.schemaextraction;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;
import org.hypergraphql.config.system.ServiceConfig;

import java.io.*;
import java.util.List;

/**
 * Coordinates the schema extraction by extracting the schema from each service separately and merging them into one
 * HGQL schema.
 */
public class ExtractionController {


    private List<ServiceConfig>  serviceConfigs;
    MappingConfig mapping;
    SPARQLExtraction extractor;
    RDFtoHGQL mapper;

    public ExtractionController(List<ServiceConfig> serviceConfigs, Model mapping, String query_template){
        this.serviceConfigs = serviceConfigs;
        this.mapping = new MappingConfig(mapping);
        this.extractor = new SPARQLExtraction(this.mapping,query_template);
        this.mapper = new RDFtoHGQL(this.mapping);
    }

    /**
     * Extracts the schema from the services available in the serviceConfigs attribute and merges the schemata in one
     * HGQL schema.
     */
    public void extractAndMap(){
        for (ServiceConfig conf: serviceConfigs) {
            Model serviceSchema = this.extractor.extractSchema(conf.getUrl(), conf.getUser(), conf.getPassword());
            this.mapper.create(serviceSchema, conf.getId());
        }
    }

    public String getHGQLSchema(){
        return this.mapper.buildSDL();
    }

    public Reader getHGQLSchemaReader(){
        return new StringReader(this.mapper.buildSDL());
    }
}
