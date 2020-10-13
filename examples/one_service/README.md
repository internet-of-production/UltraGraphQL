# one_service Example
This example has one service from which the schema is extracted. The extracted schema is shown [here](#extracted-ugqls).
The sample dataset allows to write queries for different features of UGQL as shown in in [sample query](#sample-queries) section.


To start the UGQL instance of this example run the following command from this folder.

```bash
java -jar ../../build/libs/ultragraphql-<version>-exe.jar --config config.json
```
e.g.:
```bash
java -jar ../../build/libs/ultragraphql-1.1.0-exe.jar --config config.json
```

## Sample Queries

### Simple Query of Depth One

```graphql
{
    ex_Person{
        _id
        ex_name
        ex_age
    }
}
```

## Simple Query of Depth Two
In this query the output type of the field *ex_relatedWith* is object type of the schema allowing to query for nested objects.
In the example query the name and id of each person and the persons they are related with are queried.
```graphql
{
    ex_Person{
        _id
        ex_name
        ex_relatedWith{
            _id
            ex_name
        }
    }
}
```

## Querying Field with Multiple Output types
The field *ex_drives* has the possible output types *ex_Car* and *ex_Ecar*.
Additionally *ex_Ecar* is the subclass of *ex_Car* meaning that the set of fields of *ex_Ecar* contain all fields of *ex_Car*.
Furthermore *ex_Ecar* has the field *ex_maxCharge*.
The schema mapping process mapps the multiple output types of the field *ex_drives* to one interface *ex_drives_OutputType* with the fields of the interface being the intersection of the fields of all possible outputtypes.
Then each of the possible output types implements this interface to allow to query for those types over the interface.

This mapping procedure allows to query for fields that all types have without needing to specify the type as shown in the example queries below.

To query the brand and model of a car a person drives no type must be specified because both types have the same fields.
```graphql
{
    ex_Person{
        _id
        ex_name
        ex_drives{
            ex_brand
            ex_model
        }
    }
}
```

To query additionally the maximum charge of electric cars the type specific field can be added with an fragment.
```graphql
{
    ex_Person{
        _id
        ex_name
        ex_drives{
            ex_brand
            ex_model
            ...on ex_Ecar{
                ex_maxCharge
            }
        }
    }
}
```
Alternatively all fields can also be queried with type specific fragments for all types.
```graphql
{
    ex_Person{
        _id
        ex_name
        ex_drives{
            ...on ex_Car{
                ex_brand
                ex_model
            }
            ...on ex_Ecar{
                ex_brand
                ex_model
                ex_maxCharge
            }
        }
    }
}
```
## Querying Field with Literal Placholder as Possible Type

If a field has a literal and a object type as possible output types then the literal is mapped to the *Literal Placeholder Type* to map the RDF schema to a valid UGQLS/GQLS.
A detailed explanation of the reasons and realization of the *Literal Placecholder* concept can be found [here](../../docs/Literal_placeholder.md).

To access the literal value of the field *ex_address* the *Literal Placeholder* (**hgqls_Literal**) must be queried with an type specifying fragment as shown in the example query below.
The query queries all literal values that are assigned to the field *ex_address* and for all objects of type *ex_Address* that are assigned to the field.
```graphql
{
    ex_Person{
        _id
        ex_name
        ex_address{
            ...on hgqls_Literal{
                hgqls_value
            }
            ...on ex_Address{
                ex_street_name
                ex_street_number
            }
        }
    }
}
```

## Extracted UGQLS

```graphql
type __Context{
	hgqls_Literal:	_@href(iri:"http://hypergraphql.org/schema/Literal")
	ex_brand:	_@href(iri:"http://example.org/brand")
	ex_Person:	_@href(iri:"http://example.org/Person")
	ex_maxCharge:	_@href(iri:"http://example.org/maxCharge")
	ex_Address:	_@href(iri:"http://example.org/Address")
	ex_Car:	_@href(iri:"http://example.org/Car")
	ex_street_number:	_@href(iri:"http://example.org/street_number")
	ex_drives:	_@href(iri:"http://example.org/drives")
	ex_Ecar:	_@href(iri:"http://example.org/Ecar")
	ex_model:	_@href(iri:"http://example.org/model")
	rdf_type:	_@href(iri:"http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
	ex_color:	_@href(iri:"http://example.org/color")
	ex_age:	_@href(iri:"http://example.org/age")
	hgqls_value:	_@href(iri:"http://hypergraphql.org/schema/value")
	ex_relatedWith:	_@href(iri:"http://example.org/relatedWith")
	ex_street_name:	_@href(iri:"http://example.org/street_name")
	ex_address:	_@href(iri:"http://example.org/address")
	ex_name:	_@href(iri:"http://example.org/name")
}
interface ex_Address_Interface {
	ex_street_name: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_street_number: [String] @service(id: "dataset")
}
interface hgqls_Literal_Interface {
	hgqls_value: [String] 
}
interface ex_Ecar_Interface {
	ex_model: [String] @service(id: "dataset")
	ex_maxCharge: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_color: [String] @service(id: "dataset")
	ex_brand: [String] @service(id: "dataset")
}
interface ex_Person_Interface {
	ex_relatedWith: [ex_Person] @service(id: "dataset")
	ex_age: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_address: [ex_address_OutputType] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
	ex_drives: [ex_drives_OutputType] @service(id: "dataset")
}
interface ex_Car_Interface {
	ex_model: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_color: [String] @service(id: "dataset")
	ex_brand: [String] @service(id: "dataset")
}
interface ex_drives_OutputType {
 ex_model: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_color: [String] @service(id: "dataset")
	ex_brand: [String] @service(id: "dataset")
}
interface ex_address_OutputType {
 
}
type hgqls_Literal implements ex_address_OutputType & hgqls_Literal_Interface @service(id: "dataset") {
 	hgqls_value: [String] 
}
type ex_Person implements ex_Person_Interface @service(id: "dataset") {
 	ex_relatedWith: [ex_Person] @service(id: "dataset")
	ex_age: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_address: [ex_address_OutputType] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
	ex_drives: [ex_drives_OutputType] @service(id: "dataset")
}
type ex_Address implements ex_address_OutputType & ex_Address_Interface @service(id: "dataset") {
 	ex_street_name: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_street_number: [String] @service(id: "dataset")
}
type ex_Car implements ex_Car_Interface & ex_drives_OutputType @service(id: "dataset") {
 	ex_model: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_color: [String] @service(id: "dataset")
	ex_brand: [String] @service(id: "dataset")
}
type ex_Ecar implements ex_Car_Interface & ex_drives_OutputType & ex_Ecar_Interface @service(id: "dataset") {
 	ex_model: [String] @service(id: "dataset")
	ex_maxCharge: [String] @service(id: "dataset")
	rdf_type: [String] @service(id: "dataset")
	ex_color: [String] @service(id: "dataset")
	ex_brand: [String] @service(id: "dataset")
}
```
