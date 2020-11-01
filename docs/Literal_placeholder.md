# Literal Placeholder
In GraphQL a Scalar value is not able to implement an interface. Rising the problem of how to realize fields with multiple output types including Scalar types like String.
During the implementation of the bootstrapping module this problem was solved by setting the output type of fields with multiple defined output types to an interface.
This interface is then implemented by each object that is a potential output type of the field. The fields of the output type are the intersection of all sets of fields of objects that implement the interface.
Is now a scalar one of those possible output types then this approach does not work because of the earlier mentioned limitations of scalars.
To circumvent this the **Literal placeholder** object was introduced. This object is not directly queryable and is excluded from the query set. It main purpose is to provide the access to the literals eventhough the GraphQL does not allow a direct query.
Therfore the Literal placeholder object implements all interfaces that actually the scalar String had to implement. The only the field of this object is a field linking to the actual literal.

During the generation of the JSON representation/ execution forest the literal placeholder object (object + fields) is always treaded as if the same service is defined for them as for the root field.
In the example this would mean that the placeholder object had the service of the field ex_address.
To query the correct data from the altered query the placeholder object is ignored during the query translation meaning that the variable of hgqls_Literal is used to query for the literals.
Additionally a filter has to be applied because we know that the field has multiple output types meaning that the range of the property will contain resources that are not Literals.
Now the literals of ex_address are stored under the variable of hgqls_Literal. These results must be adopted so that the type resolver assigned to the GraphQL schema can correctly resolve the type and map it to GraphQL response from the data in the result pool.
In order to comply with this requirements the storing procedure for the placeholder object is altered.
A virtual resource is created of type hgqls_Literal to which all strings are assigned.
For the example this means instead of
>ex:Bob ex:address "742 Evergreen Terrace".

the following triples are inserted into the result pool:
>ex:Bob ex:address  hgqls:x_1_1_y_2.
>hgqls:x_1_1_y_2 rdf:type hgqls:Literal.
>hgqls:x_1_1_y_2 hgqls:string "742 Evergreen Terrace".


With this structure the type resolver are able to extract the data correctly.
For the placeholder object and field the name space hgqls was chosen to avoid incompatibility issues with existing resources like rdf:Literal.
>The name and name space of the placeholder object can be configured in the HGQL_Vocabulary class, but it has to be considered that changing the default value can lead to incompatibility issues and incase the object name ic changed it would also require to adjust the extraction query.

>It should be mentioned that the placeholder object for literals is only generated if besides a String as output type also at least one other output type is defined.
## Example
### Dataset
```
ex:Bob a ex:Person;
    ex:name "Bob";
    ex:name "Andrews";
    ex:address "742 Evergreen Terrace";
    ex:address ex:addr742ET .

ex:addr742ET a ex:Address;
    ex:street_name "Evergreen Terrace";
    ex:house_number "742".
```
### HGQL Schema
```GraphQl
interface ex_Address_Interface {

	ex_street_name: [String] @service(id: "dataset")
	ex_house_number: [String] @service(id: "dataset")
}
interface hgqls_Literal_Interface {
	hgqls_string: [String]
}
interface ex_Person_Interface {
	ex_address: [ex_address_OutputType] @service(id: "dataset")

	ex_name: [String] @service(id: "dataset")
}
interface ex_address_OutputType {

}
type hgqls_Literal implements hgqls_Literal_Interface & ex_address_OutputType @service(id: "dataset") {
 	hgqls_string: [String]
}
type ex_Person implements ex_Person_Interface @service(id: "dataset") {
 	ex_address: [ex_address_OutputType] @service(id: "dataset")
	ex_name: [String] @service(id: "dataset")
}
type ex_Address implements ex_address_OutputType & ex_Address_Interface @service(id: "dataset") {
 	ex_street_name: [String] @service(id: "dataset")
	ex_house_number: [String] @service(id: "dataset")
}

```

### GraphQL query
```
{ex_Person
    ex_name
    ex_address{
      ...on hgqls_Literal{
        hgqls_value
      }
      ...on ex_Address{
        ex_house_number
      }
    }
}
```

### UGQL Representation of the Query

> As from **UGQL 1.1.0 or higher** the JSON representation of the query is replaced by java objects with the same structure.
> The JSON object below is therefore still valid to express the structure and naming of the UGQL representation of the GQL query since the SPARQL variable naming scheme did not change.

```json
{
    "name": "ex_Person",
    "alias": null,
    "parentId": null,
    "nodeId": "x_1",
    "args": null,
    "targetName": "ex_Person",
    "fields":
    [
        {
            "name": "ex_address",
            "alias": null,
            "parentId": "x_1",
            "nodeId": "x_1_1_y_1",
            "args": null,
            "targetName": "ex_Address",
            "fields":
            [
                {
                    "name": "ex_house_number",
                    "alias": null,
                    "parentId": "x_1_1_y_1",
                    "nodeId": "x_1_1_y_1_1",
                    "args": null,
                    "targetName": "String",
                    "fields": null
                }
            ]
        },
        {
            "name": "ex_address",
            "alias": null,
            "parentId": "x_1",
            "nodeId": "x_1_1_y_2",
            "args": null,
            "targetName": "hgqls_Literal",
            "fields":
            [
                {
                    "name": "hgqls_string",
                    "alias": null,
                    "parentId": "x_1_1_y_2",
                    "nodeId": "x_1_1_y_2_1",
                    "args": null,
                    "targetName": "String",
                    "fields": null
                }
            ]
        },
        {
            "name": "ex_name",
            "alias": null,
            "parentId": "x_1",
            "nodeId": "x_1_2",
            "args": null,
            "targetName": "String",
            "fields": null
        }
    ]
}
```


### SPARQL Translation of the Query
```sparql
SELECT *
WHERE
{
    {   SELECT ?x_1
        WHERE {
            ?x_1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        }
    }
    OPTIONAL {
        ?x_1 <http://example.org/address> ?x_1_1_y_1 .
        ?x_1_1_y_1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Address> .
        OPTIONAL {
        ?x_1_1_y_1 <http://example.org/house_number> ?x_1_1_y_1_1 .
        }
    }
    OPTIONAL {
        ?x_1 <http://example.org/address> ?x_1_1_y_2 .
        FILTER(isLiteral(?x_1_1_y_2))
    }
    OPTIONAL {
        ?x_1 <http://example.org/name> ?x_1_2 .
    }
}

```







### Result enhancement

The result of the query is a mapping from result resources (IRIs and Literals) to corresponding query variables. 
During the result transformation the results for the literal placeholder need to be converted to the literal placeholder structure to keep the result schema compliant.
The results are therefore inserted with the structural overhead of the placeholder structure to the final result format.
