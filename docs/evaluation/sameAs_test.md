# Test of the sameAs feature
This test is preformed [here](../../src/test/java/org/hypergraphql/ApplicationTest.java) in the sameAsTest() method.
## Test Setup
One dataset with sameAs definitions between classes and properties. To extract and map the schema the defaults were used.

## Expected Outcome
By querying *eg_Person*, which is the same as dbo_Person, also the *dbo_Person* results MUST be queried and returned.
The same holds for the property *rdfs_label* and *ex_label* if one is queried the results of both MUST be returned.
The response

## ExtractedHGQL Schema
```graphql
type __Context{
	dbo_address:	_@href(iri:"http://dbpedia.org/ontology/address")
	rdf_type:	_@href(iri:"http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
	eg_Person:	_@href(iri:"http://www.example.org/Person")
	eg_label:	_@href(iri:"http://www.example.org/label")
	rdfs_label:	_@href(iri:"http://www.w3.org/2000/01/rdf-schema#label")
	eg_Address:	_@href(iri:"http://www.example.org/Address")
	dbo_street_number:	_@href(iri:"http://dbpedia.org/ontology/street_number")
	dbo_Address:	_@href(iri:"http://dbpedia.org/ontology/Address")
	dbo_street_name:	_@href(iri:"http://dbpedia.org/ontology/street_name")
	dbo_Person:	_@href(iri:"http://dbpedia.org/ontology/Person")
	eg_street:	_@href(iri:"http://www.example.org/street")
	eg_address:	_@href(iri:"http://www.example.org/address")
}
interface dbo_Address_Interface {
	dbo_street_number: [String] @service(id: "dataset_1")
	rdf_type: [String] @service(id: "dataset_1")
	dbo_street_name: [String] @service(id: "dataset_1")
}
interface eg_Person_Interface {
	eg_address: [eg_Address] @service(id: "dataset_1")
	rdfs_label: [String] @schema(sameAs: "eg_label") @service(id: "dataset_1")
	rdf_type: [String] @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}
interface eg_Address_Interface {
	rdf_type: [String] @service(id: "dataset_1")
	eg_street: [String] @service(id: "dataset_1")
}
interface dbo_Person_Interface {
	dbo_address: [dbo_Address] @service(id: "dataset_1")
	rdfs_label: [String] @schema(sameAs: "eg_label") @service(id: "dataset_1")
	rdf_type: [String] @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}

type eg_Person implements dbo_Person_Interface & eg_Person_Interface @service(id: "dataset_1") @schema(sameAs: "dbo_Person") {
 	dbo_address: [dbo_Address] @service(id: "dataset_1")
	eg_address: [eg_Address] @service(id: "dataset_1")
	rdfs_label: [String] @schema(sameAs: "eg_label") @service(id: "dataset_1")
	rdf_type: [String] @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}
type eg_Address implements eg_Address_Interface @service(id: "dataset_1") {
 	rdf_type: [String] @service(id: "dataset_1")
	eg_street: [String] @service(id: "dataset_1")
}
type dbo_Address implements dbo_Address_Interface @service(id: "dataset_1") {
 	dbo_street_number: [String] @service(id: "dataset_1")
	rdf_type: [String] @service(id: "dataset_1")
	dbo_street_name: [String] @service(id: "dataset_1")
}
type dbo_Person implements dbo_Person_Interface & eg_Person_Interface @service(id: "dataset_1") @schema(sameAs: "eg_Person") {
 	dbo_address: [dbo_Address] @service(id: "dataset_1")
	eg_address: [eg_Address] @service(id: "dataset_1")
	rdfs_label: [String] @schema(sameAs: "eg_label") @service(id: "dataset_1")
	rdf_type: [String] @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}

```

## HGQL Query
```graphql
{
    eg_Person{
        _id
        rdfs_label
    }
}
```

## SPARQL Query
```sparql
SELECT *
WHERE {
    {
        SELECT ?x_1
        WHERE {
            VALUES ?sameas {
                <http://dbpedia.org/ontology/Person>
                <http://www.example.org/Person>
            }
            ?x_1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?sameas .
        }
    }
    OPTIONAL {
        ?x_1 <http://www.w3.org/2000/01/rdf-schema#label>|<http://www.example.org/label> ?x_1_1 .
    }
}
```

## GRAPHQL Response

```graphql
{
  "extensions":{},
  "data":
    {
        "eg_Person":[
            {
                "rdfs_label":["Bob"],
                "_id":"http://www.example.org/bob"
            },
            {
                "rdfs_label":["Alice"],
                "_id":"http://www.example.org/alice"
            }
        ],
        "@context":
            {
                "eg_Person":"http://hypergraphql.org/query/eg_Person",
                "rdfs_label":"http://www.w3.org/2000/01/rdf-schema#label",
                "_type":"@type",
                "_id":"@id"
            }
    },
"errors":[]
}

```



## Dataset

```turtle
@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
@prefix dbo:   <http://dbpedia.org/ontology/> .
@prefix dbr:   <http://dbpedia.org/resource/> .
@prefix ex:   <http://www.example.org/> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .

ex:alice a ex:Person ;
        rdfs:label "Alice";
        ex:address ex:addr_a .

ex:addr_a a ex:Address;
        ex:street "123 Fake Street" .

ex:bob a dbo:Person ;
        ex:label "Bob" ;
        dbo:address  dbr:addr_b .

dbr:addr_b a dbo:Address ;
        dbo:street_number "742" ;
        dbo:street_name "Evergreen Terrac" .

ex:Person owl:sameAs dbo:Person .

ex:label owl:sameAs rdfs:label .
```
