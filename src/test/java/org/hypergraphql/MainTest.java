package org.hypergraphql;


import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.hypergraphql.Test.ServerStart;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MainTest {

//   @Test
//   public void testtest(){
//
       public static void main(String[] args) {

//       ServerStart server1 = new ServerStart("src/test/resources/properties.json");
//       ServerStart server2 = new ServerStart("src/test/resources/externalHGQLserver/properties.json");
//       server1.run();
//       server2.run();



           String construct1 = "PREFIX dbpedia: <http://dbpedia.org/ontology/>" +
                   "PREFIX  foaf: <http://xmlns.com/foaf/0.1/>" +
                   "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>" +
                   "CONSTRUCT { ?person a <http://dbpedia.org/ontology/Person> . " +
                   "?person <http://xmlns.com/foaf/0.1/name> ?name . ?person rdfs:label ?label . ?person dbpedia:birthPlace ?birthplace . " +
                   "?birthplace rdfs:label ?birthplaceLabel }" +
                   "WHERE\n" +
                   "  { ?person a <http://dbpedia.org/ontology/Person> . " +
                   "?person <http://xmlns.com/foaf/0.1/name> ?name . ?person rdfs:label ?label . ?person dbpedia:birthPlace ?birthplace . " +
                   "?birthplace rdfs:label ?birthplaceLabel }\n" +
                   "LIMIT   10\n" +
                   "";

           String construct = "PREFIX dbpedia: <http://dbpedia.org/ontology/>" +
                   "PREFIX  foaf: <http://xmlns.com/foaf/0.1/>" +
                   "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>" +
                   "CONSTRUCT { ?person a <http://dbpedia.org/ontology/Person> . " +
                   "?person <http://xmlns.com/foaf/0.1/name> ?name . ?person rdfs:label ?label }" +
                   "WHERE\n" +
                   "  { ?person a <http://dbpedia.org/ontology/Person> . " +
                   "?person <http://xmlns.com/foaf/0.1/name> ?name . ?person rdfs:label ?label }\n" +
                   "LIMIT   20\n" +
                   "";


           Query query = QueryFactory.create(construct); //s2 = the query above
           QueryExecution qExe = QueryExecutionFactory.sparqlService( "http://dbpedia.org/sparql", query );


           Model model1 = qExe.execConstruct();



           System.out.println(model1);

           Property property = model1.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
           RDFNode node = model1.createResource("http://dbpedia.org/ontology/Person");



           ResIterator nodes = model1.listResourcesWithProperty(property,node);

           Model model2 = ModelFactory.createDefaultModel();

           while (nodes.hasNext()) {

               ParameterizedSparqlString pss = new ParameterizedSparqlString();

               pss.setCommandText("PREFIX dbpedia: <http://dbpedia.org/ontology/>\n" +
                       "PREFIX  foaf: <http://xmlns.com/foaf/0.1/>\n" +
                       "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                       "CONSTRUCT { ?person dbpedia:birthPlace ?birthplace . \n" +
                                         "?birthplace rdfs:label ?birthplaceLabel  } \n" +
                       "WHERE\n" +
                       "  { ?person dbpedia:birthPlace ?birthplace . \n" +
                       "                   ?birthplace rdfs:label ?birthplaceLabel  }\n" +
                       "LIMIT   10");

               Resource currentnode = nodes.next();
               pss.setParam("person", currentnode);
               System.out.println(pss.toString());
               query = QueryFactory.create(pss.toString());
               qExe = QueryExecutionFactory.sparqlService( "http://dbpedia.org/sparql", query );
               model2.add(qExe.execConstruct());






           }

           model2.write(System.out, "NTRIPLE");











       }

}
