# Test of the union feature of HGQL
This test is preformed [here](../../src/test/java/org/hypergraphql/ApplicationTest.java) in the unionTest() method.
Information on how unions were added to HGQL can be found [here](../union.md)
## Test Setup
To test the union feature the [schema](#hgql-schema) defines the a field with a union as output.
The GraphQL query queries both types of the field with a different Selection Set for both fields.

## Expected Outcome
It is expected that the query is able to extract the data correctly which means that for each type the corresponding SelectionSet is queried. And scince the field has a defined sameAs field the result MUST contain the results as if both fields were queried.

### HGQL Schema
```GraphQL
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
	rdf_type: [String] @service(id: "dataset_1")
	dbo_street_name: [String] @service(id: "dataset_1")
	dbo_street_number: [String] @service(id: "dataset_1")
}
interface eg_Person_Interface {
	rdf_type: [String] @service(id: "dataset_1")
	dbo_address: [dbo_address_OutputType] @schema(sameAs: "eg_address") @service(id: "dataset_1")
	rdfs_label: [String] @service(id: "dataset_1") @schema(sameAs: "eg_label")
	eg_address: [eg_address_OutputType] @schema(sameAs: "dbo_address") @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}
interface eg_Address_Interface {
	rdf_type: [String] @service(id: "dataset_1")
	eg_street: [String] @service(id: "dataset_1")
}
interface dbo_Person_Interface {
	rdf_type: [String] @service(id: "dataset_1")
	dbo_address: [dbo_address_OutputType] @schema(sameAs: "eg_address") @service(id: "dataset_1")
	rdfs_label: [String] @service(id: "dataset_1") @schema(sameAs: "eg_label")
	eg_address: [eg_address_OutputType] @schema(sameAs: "dbo_address") @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}
union dbo_address_OutputType = dbo_Address | eg_Address
union eg_address_OutputType = dbo_Address | eg_Address
type eg_Person implements dbo_Person_Interface & eg_Person_Interface @schema(sameAs: "dbo_Person") @service(id: "dataset_1") {
 	rdf_type: [String] @service(id: "dataset_1")
	dbo_address: [dbo_address_OutputType] @schema(sameAs: "eg_address") @service(id: "dataset_1")
	rdfs_label: [String] @service(id: "dataset_1") @schema(sameAs: "eg_label")
	eg_address: [eg_address_OutputType] @schema(sameAs: "dbo_address") @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}
type eg_Address implements eg_Address_Interface @service(id: "dataset_1") {
 	rdf_type: [String] @service(id: "dataset_1")
	eg_street: [String] @service(id: "dataset_1")
}
type dbo_Address implements dbo_Address_Interface @service(id: "dataset_1") {
 	rdf_type: [String] @service(id: "dataset_1")
	dbo_street_name: [String] @service(id: "dataset_1")
	dbo_street_number: [String] @service(id: "dataset_1")
}
type dbo_Person implements dbo_Person_Interface & eg_Person_Interface @schema(sameAs: "eg_Person") @service(id: "dataset_1") {
 	rdf_type: [String] @service(id: "dataset_1")
	dbo_address: [dbo_address_OutputType] @schema(sameAs: "eg_address") @service(id: "dataset_1")
	rdfs_label: [String] @service(id: "dataset_1") @schema(sameAs: "eg_label")
	eg_address: [eg_address_OutputType] @schema(sameAs: "dbo_address") @service(id: "dataset_1")
	eg_label: [String] @service(id: "dataset_1") @schema(sameAs: "rdfs_label")
}
```
### GraphQL Query
```GraphQL
{
  eg_Person{
    _id
    rdfs_label
    eg_address{
      ... on eg_Address{
        _id
        eg_street
      }
      ... on dbo_Address{
        _id
        dbo_street_name
        dbo_street_number
      }
    }
  }
}
```
### HGQL Query
```SPARQL
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
    ?x_1 <http://www.example.org/address>|<http://dbpedia.org/ontology/address> ?x_1_1_y_1 .
    ?x_1_1_y_1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.example.org/Address> .
    OPTIONAL {
      ?x_1_1_y_1 <http://www.example.org/street> ?x_1_1_y_1_1 .
    }
  }
  OPTIONAL {
    ?x_1 <http://www.example.org/address>|<http://dbpedia.org/ontology/address> ?x_1_1_y_1_2 .
    ?x_1_1_y_1_2 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Address> .
    OPTIONAL {
      ?x_1_1_y_1_2 <http://dbpedia.org/ontology/street_name> ?x_1_1_y_1_2_1 .
    }
    OPTIONAL {
      ?x_1_1_y_1_2 <http://dbpedia.org/ontology/street_number> ?x_1_1_y_1_2_2 .
    }
  }
  OPTIONAL {
    ?x_1 <http://www.w3.org/2000/01/rdf-schema#label>|<http://www.example.org/label> ?x_1_4 .
  }
}
```
### GraphQL Result
```GraphQL
{
  "extensions":{},
    "data":{
      "eg_Person":[
        {
          "rdfs_label":["Bob"],
          "_id":"http://www.example.org/bob",
          "eg_address":[
            {
              "dbo_street_number":["742"],
              "dbo_street_name":["Evergreen Terrace"]
            }
          ]
        },
        {
          "rdfs_label":["Alice"],
          "_id":"http://www.example.org/alice",
          "eg_address":[
            {
              "_id":"http://www.example.org/addr_a",
              "eg_street":["123 Fake Street"]
            }
          ]
        }
    ],
    "@context":{
      "eg_Person":"http://hypergraphql.org/query/eg_Person",
      "rdfs_label":"http://www.w3.org/2000/01/rdf-schema#label",
      "_type":"@type",
      "dbo_street_number":"http://dbpedia.org/ontology/street_number",
      "_id":"@id",
      "dbo_street_name":"http://dbpedia.org/ontology/street_name",
      "eg_address":"http://www.example.org/address",
      "eg_street":"http://www.example.org/street"
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
        dbo:street_name "Evergreen Terrace" .

ex:Person owl:sameAs dbo:Person .

ex:label owl:sameAs rdfs:label .

ex:address owl:sameAs dbo:address .
```
