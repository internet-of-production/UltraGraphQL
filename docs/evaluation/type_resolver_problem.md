#Type Resolver Problem
> As from **UGQL v1.1.0 or higher** this problem does not exist anymore. No if a resource has multiple types the fields of the different types are merged.
>Furthermore, if a schema violation does occur because of the merging a safe fail was integrated. 
>Meaning that if a field output type is defined as NOT list (single output) but the merging results in more than one result, than the output type for the file is changed to list for this result and marked in the error segment of the result.
>
Fields with the output types interface and union can output differrent object types.
GraphQL therefore need to evaluate the type of result values inorder to call the corresponding data fetcher in order to query type related data.
For this purpose a type resolver is assigned to each field with an interface or union as output type.
The problem that now occurs is that the type resolver is only allowed to return one GraphQL type as result.
In cases were a result value can be resolved to multiple types with at least two of these types queried over one field only one data fetcher for this value can be colled.
Resulting in an incomplete result as shown in the example below.

In the example the entity ex:Fox has the two types  ex:Polo and ex:Fox. 
Both types are possible output types of the property ex:drives.
If the field ex_drives is queried for both types then only the information for ex:Fox of one type are returned because only one of the datafetchers was called.

## Dataset
```turtle
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix schema: <http://schema.org/> .
@prefix hgqls: <http://hypergraphql.org/mapping/> .
@prefix ex: <http://example.org/> .



ex:Bob a ex:Person;
    ex:name "Bob";
    ex:name "Andrews";
    ex:address "742 Evergreen Terrace";
    ex:drives ex:polo;
    ex:drives ex:fox;
    ex:address ex:addr742ET .

ex:addr742ET a ex:Address;
    ex:street_name "Evergreen Terrace";
    ex:house_number "742".

ex:polo a ex:Polo;
    ex:model "polo".

ex:fox a ex:Fox;
    a ex:Polo;
    ex:name "Fox".
```

## Extracted Schema
```graphql
type __Context{
	hgqls_Literal:	_@href(iri:"http://hypergraphql.org/schema/Literal")
	ex_Fox:	_@href(iri:"http://example.org/Fox")
	ex_Person:	_@href(iri:"http://example.org/Person")
	ex_Address:	_@href(iri:"http://example.org/Address")
	ex_drives:	_@href(iri:"http://example.org/drives")
	ex_model:	_@href(iri:"http://example.org/model")
	rdf_type:	_@href(iri:"http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
	hgqls_string:	_@href(iri:"http://hypergraphql.org/schema/string")
	ex_Polo:	_@href(iri:"http://example.org/Polo")
	ex_street_name:	_@href(iri:"http://example.org/street_name")
	ex_address:	_@href(iri:"http://example.org/address")
	ex_house_number:	_@href(iri:"http://example.org/house_number")
	ex_name:	_@href(iri:"http://example.org/name")
}
interface ex_Address_Interface {
	rdf_type: [String] @service(id: "dataset")
	ex_house_number: [String] @service(id: "dataset")
	ex_street_name: [String] @service(id: "dataset")
}
interface hgqls_Literal_Interface {
	hgqls_string: [String] 
}
interface ex_Person_Interface {
	rdf_type: [String] @service(id: "dataset")
	ex_drives: [ex_drives_OutputType] @service(id: "dataset")
	ex_address: [ex_address_OutputType] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
}
interface ex_Fox_Interface {
	rdf_type: [String] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
}
interface ex_Polo_Interface {
	rdf_type: [String] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
	ex_model: [String] @service(id: "dataset")
}
interface ex_address_OutputType {
 
}
interface ex_drives_OutputType {
 rdf_type: [String] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
}
type hgqls_Literal implements ex_address_OutputType & hgqls_Literal_Interface @service(id: "dataset") {
 	hgqls_string: [String] 
}
type ex_Fox implements ex_Fox_Interface & ex_drives_OutputType @service(id: "dataset") {
 	rdf_type: [String] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
}
type ex_Person implements ex_Person_Interface @service(id: "dataset") {
 	rdf_type: [String] @service(id: "dataset")
	ex_drives: [ex_drives_OutputType] @service(id: "dataset")
	ex_address: [ex_address_OutputType] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
}
type ex_Address implements ex_address_OutputType & ex_Address_Interface @service(id: "dataset") {
 	rdf_type: [String] @service(id: "dataset")
	ex_house_number: [String] @service(id: "dataset")
	ex_street_name: [String] @service(id: "dataset")
}
type ex_Polo implements ex_drives_OutputType & ex_Polo_Interface @service(id: "dataset") {
 	rdf_type: [String] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
	ex_model: [String] @service(id: "dataset")
}
```
## GrapQL Query
```graphql
{ex_Person{
  _id
  ex_drives{
    ...on ex_Polo{
      _id
      ex_model
      ex_name
    }
    ...on ex_Fox{
      _id
      ex_name
      
    }
  }
}}
```
## Query Response
```json
{
  "extensions": {},
  "data": {
    "ex_Person": [
      {
        "_id": "http://example.org/Bob",
        "ex_drives": [
          {
            "_id": "http://example.org/polo",
            "ex_model": [
              "polo"
            ],
            "ex_name": []
          },
          {
            "_id": "http://example.org/fox",
            "ex_name": [
              "Fox"
            ]
          }
        ]
      }
    ],
    "@context": {
      "ex_Person": "http://hypergraphql.org/query/ex_Person",
      "_type": "@type",
      "_id": "@id",
      "ex_drives": "http://example.org/drives",
      "ex_name": "http://example.org/name",
      "ex_model": "http://example.org/model"
    }
  },
  "errors": []
}
```