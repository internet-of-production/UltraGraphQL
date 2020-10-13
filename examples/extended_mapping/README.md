# extended_mapping Example combined with Equivalence Relations
In this example the mapping configuration is extended with additional schema vocabulary.
The equivalence relations are extended with the term **ex:equivalance**.
With this extension of the default mapping vocabulary now also relations defined with the new terma are extracted and mapped.

>Note: The default extraction query uses a implicit extraction method meaning that the resources must not be directly be defined as class or property to be extracted. 
>Primarily this means that changing or extending the mapping vocabulary might not extend the extracted schema. 
>For controlled extraction of the schema a revision of the extraction query is recommended.

To start the UGQL instance of this example run the following command from this folder.

```bash
java -jar ../../build/libs/ultragraphql-1.1.0-exe.jar --config config.json
```

In this example we have two datasets [A](dataset_a.ttl) and [B](dataset_b.ttl) with equivalence relations define in one dataset (B).
There are equivalences defined with the default mapping vocabulary and with the newly defined term between classes and properties.
From the [generated UGQL schema](#extracted-ugqls) it can be seen that the equivalences between schema resources are mapped to equivalence relations in the UGQLS.
Whereas equivalence relations between actual data entites are mapped to fields that are queryable.
Compare for this behavior the equivalence relation between *ex:Person* and *foaf:Person* with the relation between *ex:doc_1* and *ex:doc_2*.
Furthermore, it can be seen that the defined equivalence relations lead to internally querying both services if either ex:Person or foaf:Person is queried.
The following query returns the data of both datasets/services by using only schema entities that are only found at one of the services.
```graphql
{
   ex_Person{
      _id
      ex_name
      foaf_age
   }
}
```
The query above leads to the following response:
```json
{
  "extensions": {},
  "data": {
    "@context": {
      "_type": "@type",
      "_id": "@id",
      "foaf_Person": "http://xmlns.com/foaf/0.1/Person",
      "foaf_age": "http://xmlns.com/foaf/0.1/age",
      "ex_name": "http://example.org/name"
    },
    "foaf_Person": [
      {
        "foaf_age": [
          "42"
        ],
        "_id": "http://example.org/bob",
        "ex_name": [
          "Bob"
        ]
      },
      {
        "foaf_age": [
          "41"
        ],
        "_id": "http://example.org/alice",
        "ex_name": [
          "Alice"
        ]
      }
    ]
  },
  "errors": []
}
```

The result is semantically correct because by considering the defined datasets as one dataset the equivalences are valid across all services.
Even though Alice is not defined as **ex:Person** it is implied through the equivalence relation and therefore defining Alice as *ex:Person* is valid in the result.
The same holds for the there equivalence relations.


## Extracted UGQLS

```graphql
type __Context{
	hgqls_Literal:	_@href(iri:"http://hypergraphql.org/schema/Literal")
	foaf_name:	_@href(iri:"http://xmlns.com/foaf/0.1/name")
	ex_Document:	_@href(iri:"http://example.org/Document")
	ex_Person:	_@href(iri:"http://example.org/Person")
	rdfs_label:	_@href(iri:"http://www.w3.org/2000/01/rdf-schema#label")
	foaf_Document:	_@href(iri:"http://xmlns.com/foaf/0.1/Document")
	rdf_type:	_@href(iri:"http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
	ex_age:	_@href(iri:"http://example.org/age")
	foaf_Person:	_@href(iri:"http://xmlns.com/foaf/0.1/Person")
	owl_equivalentClass:	_@href(iri:"http://www.w3.org/2002/07/owl#equivalentClass")
	hgqls_value:	_@href(iri:"http://hypergraphql.org/schema/value")
	foaf_age:	_@href(iri:"http://xmlns.com/foaf/0.1/age")
	ex_name:	_@href(iri:"http://example.org/name")
}
interface foaf_Person_Interface {
	ex_name: [String] @schema(sameAs: "foaf_name") @service(id: ["dataset_a", "dataset_b"])
	ex_age: [String] @schema(sameAs: "foaf_age") @service(id: ["dataset_a", "dataset_b"])
	foaf_age: [String] @schema(sameAs: "ex_age") @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
	foaf_name: [String] @schema(sameAs: "ex_name") @service(id: ["dataset_a", "dataset_b"])
}
interface hgqls_Literal_Interface {
	hgqls_value: [String] 
}
interface ex_Person_Interface {
	ex_name: [String] @schema(sameAs: "foaf_name") @service(id: ["dataset_a", "dataset_b"])
	ex_age: [String] @schema(sameAs: "foaf_age") @service(id: ["dataset_a", "dataset_b"])
	foaf_age: [String] @schema(sameAs: "ex_age") @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
	foaf_name: [String] @schema(sameAs: "ex_name") @service(id: ["dataset_a", "dataset_b"])
}
interface foaf_Document_Interface {
	rdfs_label: [String] @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
}
interface ex_Document_Interface {
	owl_equivalentClass: [foaf_Document] @service(id: "dataset_a")
	rdfs_label: [String] @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
}

type hgqls_Literal implements hgqls_Literal_Interface @service(id: ["dataset_a", "dataset_b"]) {
 	hgqls_value: [String] 
}
type foaf_Person implements foaf_Person_Interface & ex_Person_Interface @service(id: ["dataset_a", "dataset_b"]) @schema(sameAs: ["foaf_Person", "ex_Person"]) {
 	ex_name: [String] @schema(sameAs: "foaf_name") @service(id: ["dataset_a", "dataset_b"])
	ex_age: [String] @schema(sameAs: "foaf_age") @service(id: ["dataset_a", "dataset_b"])
	foaf_age: [String] @schema(sameAs: "ex_age") @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
	foaf_name: [String] @schema(sameAs: "ex_name") @service(id: ["dataset_a", "dataset_b"])
}
type ex_Document implements ex_Document_Interface @service(id: "dataset_a") {
 	owl_equivalentClass: [foaf_Document] @service(id: "dataset_a")
	rdfs_label: [String] @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
}
type ex_Person implements foaf_Person_Interface & ex_Person_Interface @service(id: ["dataset_a", "dataset_b"]) @schema(sameAs: ["foaf_Person", "ex_Person"]) {
 	ex_name: [String] @schema(sameAs: "foaf_name") @service(id: ["dataset_a", "dataset_b"])
	ex_age: [String] @schema(sameAs: "foaf_age") @service(id: ["dataset_a", "dataset_b"])
	foaf_age: [String] @schema(sameAs: "ex_age") @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
	foaf_name: [String] @schema(sameAs: "ex_name") @service(id: ["dataset_a", "dataset_b"])
}
type foaf_Document implements foaf_Document_Interface @service(id: ["dataset_a", "dataset_b"]) {
 	rdfs_label: [String] @service(id: ["dataset_a", "dataset_b"])
	rdf_type: [String] @service(id: ["dataset_a", "dataset_b"])
}
```