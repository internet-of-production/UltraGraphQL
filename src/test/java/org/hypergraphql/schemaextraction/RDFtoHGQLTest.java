package org.hypergraphql.schemaextraction;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

class RDFtoHGQLTest {

    @Test
    void main() {
    }

    @Test
    void evaluate(){
        String inputFileName = "./src/test/resources/test_mapping/mapping.ttl";
        Model mapping = ModelFactory.createDefaultModel();
        try {
            mapping.read(new FileInputStream(inputFileName),null,"TTL");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final MappingConfig mappingConfig = new MappingConfig(mapping);
        int n = 100;
        int iter = 100;
        Model model = ModelFactory.createDefaultModel();
        FileWriter fileWriter = createFile("evaluation/mapping_evaluation/results_only_literal_v2.csv");
        for (int i=0; i<n; i++){
            addClassWithProperty(model, i, 5, true);
            double[] calcMEDIAN = new double[iter];
            String hgqls = "";
            for(int j=0; j<iter;j++){
                RDFtoHGQL converter = new RDFtoHGQL(mappingConfig);
                final long start = System.nanoTime();
                converter.create(model, "dataset");
                hgqls = converter.buildSDL();
                final long end = System.nanoTime();
                //System.out.print(hgqls);
                double elapsedTime = (end - start) / Math.pow(10,9);
                calcMEDIAN[j] = elapsedTime;
            }
            double median = median(calcMEDIAN);
            try {
                fileWriter.write(String.format("%d; %s; %s\n" , i+1, median, hgqls.length()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.print(String.format("%d; %s; %s\n" , i, median, hgqls.length()));
        }
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Run Test again with types as outputtype
        model = ModelFactory.createDefaultModel();
        fileWriter = createFile("evaluation/mapping_evaluation/results_type_v2.csv");
        for (int i=0; i<n; i++){
            addClassWithProperty(model, i, 5, false);
            double[] calcMEDIAN = new double[iter];
            String hgqls = "";
            for(int j=0; j<iter;j++){
                RDFtoHGQL converter = new RDFtoHGQL(mappingConfig);
                final long start = System.nanoTime();
                converter.create(model, "dataset");
                hgqls = converter.buildSDL();
                final long end = System.nanoTime();
                //System.out.print(hgqls);
                double elapsedTime = (end - start) / Math.pow(10,9);
                calcMEDIAN[j] = elapsedTime;
            }
            double median = median(calcMEDIAN);
            try {
                fileWriter.write(String.format("%d; %s; %s\n" , i+1, median, hgqls.length()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.print(String.format("%d; %s; %s\n" , i, median, hgqls.length()));
        }
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // add sameAs relations
        Model base = ModelFactory.createDefaultModel();
        for (int i=0; i<n; i++) {
            addClassWithProperty(base, i, 5, false);
        }
        Model sameasClassModel = ModelFactory.createDefaultModel().add(base);
        Model sameasProperyModel = ModelFactory.createDefaultModel().add(base);

        System.out.print(Math.pow(10,9));
        // Add equivalenceClass relations
        fileWriter = createFile("evaluation/mapping_evaluation/results_equivalent_class_v2.csv");
        int ec = 100;
        for(int i=0; i<ec; i++){
            addEquivalentClass(sameasClassModel, n);
            double[] calcMEDIAN = new double[iter];
            String hgqls = "";
            for(int j=0; j<iter;j++){
                RDFtoHGQL converter = new RDFtoHGQL(mappingConfig);
                final long start = System.nanoTime();
                converter.create(model, "dataset");
                hgqls = converter.buildSDL();
                final long end = System.nanoTime();
                //System.out.print(hgqls);
                double elapsedTime = (end - start) / Math.pow(10,9);
                calcMEDIAN[j] = elapsedTime;
            }
            double median = median(calcMEDIAN);
            try {
                fileWriter.write(String.format("%d; %s; %s\n" , i+1, median, hgqls.length()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.print(String.format("%d; %s; %s\n" , i, median, hgqls.length()));
        }
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Add equivalenceClass relations
        fileWriter = createFile("evaluation/mapping_evaluation/results_equivalent_property_v2.csv");
        int ep = 100;
        for(int i=0; i<ep; i++){
            addEquivalentProperty(sameasClassModel, n);
            double[] calcMEDIAN = new double[iter];
            String hgqls = "";
            for(int j=0; j<iter;j++){
                RDFtoHGQL converter = new RDFtoHGQL(mappingConfig);
                final long start = System.nanoTime();
                converter.create(model, "dataset");
                hgqls = converter.buildSDL();
                final long end = System.nanoTime();
                //System.out.print(hgqls);
                double elapsedTime = (end - start) / Math.pow(10,9);
                calcMEDIAN[j] = elapsedTime;
            }
            double median = median(calcMEDIAN);
            try {
                fileWriter.write(String.format("%d; %s; %s\n" , i+1, median, hgqls.length()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.print(String.format("%d; %s; %s\n" , i, median, hgqls.length()));
        }
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void addEquivalentProperty(Model model, int n) {
        Resource propertyRandom = model.createResource("http://example.org/property_"+ randInt(n + 1) + "_" + randInt(5));
        Resource propertyRandom2 = model.createResource("http://example.org/property_"+ randInt(n + 1) + "_" + randInt(5));
        model.add(propertyRandom, OWL.equivalentProperty, propertyRandom2);
    }

    private void addEquivalentClass(Model model, int n) {
        Resource randomType = model.createResource("http://example.org/class_"+ randInt(n + 1));
        Resource randomType2 = model.createResource("http://example.org/class_"+ randInt(n + 1));
        model.add(randomType, OWL.equivalentClass, randomType2);
    }

    private int randInt(int max){
        return randInt(0, max);
    }
    private int randInt(int min, int max){
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    void addClassWithProperty(Model model, int c, int p, Boolean onlyLiteral){
        Resource type = model.createResource("http://example.org/class_"+ c);
        Resource literal = model.createResource(HGQLVocabulary.HGQL_SCALAR_LITERAL_URI);
        model.add(type, RDF.type, RDFS.Class);
        // Add properties to the class
        for (int i=0; i<p; i++){
            Resource property = model.createResource("http://example.org/property_"+ c + "_" + i);
            model.add(property, RDF.type, RDF.Property);
            model.add(property, RDFS.domain, type);
            if(onlyLiteral){
                model.add(property, RDFS.range, literal);
            }else{
                Resource randomType = model.createResource("http://example.org/class_"+ randInt(c));
                model.add(property, RDFS.range, randomType);
            }

        }
    }
    FileWriter createFile(String name){
        File results_only_literal = new File(name);
        try {
            results_only_literal.createNewFile();
            System.out.println("File created: " + results_only_literal.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            FileWriter writer = new FileWriter(name);
            return writer;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private double median(double[] numArray){
        Arrays.sort(numArray);
        double median;
        if (numArray.length % 2 == 0){
            median = ((double)numArray[numArray.length/2] + (double)numArray[numArray.length/2 - 1])/2;
        }
        else{
            median = (double) numArray[numArray.length/2];
        }
        return median;
    }

}