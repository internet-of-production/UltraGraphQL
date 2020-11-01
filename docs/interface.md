# UltraGraphQL Interface Support
UGQL supports the basic GraphQL interface functionalities.
In the schema interfaces are defined as follows:
```GraphQL
interface Pet{
 name: String
 age: Int
}
type Dog implements Pet{
 name: String
 age: Int
 color: String
}
type Cat implements Pet{
 name: String
 age: Int
}
```
The interfaces defined in UGQLS have the same meaning as in GraphQL.
This means that an interface defines a set of fields a type MUST have if it implements the interface.
If a field has an interface as OutputType, then the fields of the interface are queryable in the SelectionSet of the field, and type-specific fields are queryable within InlineFragments.
By querying a field with an interface as output all types that implement this interface are queried.
Example:
```GraphQL
type Person{
  firstName:
  owns: [Pet]
}
```
```GraphQL
{
  Person{
    firstName
    owns{
      name
      age
      ...on Dog{
        color
      }
    }
  }
}
```
In this example the name and age of all pets of all persons is queried and for the dogs also the color is queried.

GraphQL allows to define blank interfacs which will be enriched by UGQL with the *_id* and *_type* field.
As usual in UGQL (and *HyperGraphQL*) only objectType and fields need a defiend service, services that are defined for interfaces or unions will be ignored.

>Currently NOT supported:
>-  union extend features
>- interface implementing interfaces (~~NOT supported by graphql-java~~  **Now supported by graphql-java v15 and higher** 
>  could know be added but requires to change the mapping from UGQLS->GQLS and also allows to optimize the mapping form RDF->UGQLS. In-depth analysis of the new possibilities required)

## Implementation
>Note: UGQL is based on HGQL meaning that some classes and attributes in UGQL still contain the HGQL name and abbreviations.

HGQL has interpreted unions and interfaces as objectTypes, which had allowed to define these types in the schema, but it was not possible to use these types in the intended way.
To add interfaceTypes to the HGQLSchema and then to the GraphQL schema, the queries are validated against, the information about these types have to be inserted into the rdfSchema (UGQL internal triplestore containing the schema that was provided to the service).
The rdfSchema is an RDF dataset containing information about the HGQLSchema.
With this dataset HGQLSchema objects are created like *FieldConfig*, *FieldOfTypeConfig*, *QueryFieldConfig* and the *TypeConfig*.
These objects contain information about the whole UGQL Schema and are used during the query resolving.
The TypeConfig class is for all types of the schema and therefore functionalities for unions and interfaces were missing.
Now a TypeConfig object can be specified as a UNION , INTERFACE or OBJECT to support the needed functions.
The created HGQLSchema is then used to generate a GraphQLSchema object.
As from UGQL v1.1.0 or higher the query execution and result transformation is completely covered by UGQL using the graphql-java framework only for query validation.
This change in the query resolving makes the generation of typeResolvers redundant and therefore typeResolvers were removed from the GQLS generation.
A detailed description on how interfaces are handled during the query resolving is given below and [here](./translation_phase.md). 

For querying the SPARQLEndpoints the GraphQL query is transformed into java objects representing the original query but additional contain SPARQL specific information like the SPARQL query variable name of a type and field.
This representation is than translated into SPARQL and executed against the assigned services.
In GraphQL queries with a interface as OutputType can have fields of the types outside the InlineFragments this means that the InlineFragments only contain type specific fields or are used to query specific types. 
If fields of the interface are queried outside a InlineFragment all types that implement this interface MUST be queried.
Therefore, "virtual" fields are created for each type that implements the interface. 
The SelectionSet of the virtual field is composed of the queried fields of the interface and the for this type defined type-specific fields. 
The diagram below shows this process of generating the virual fields for received GQL queries.

>![Conversion of interfaceType to virtual query fields](./figures/TypeResolver_JsonQuery.svg)
>As from **UGQL v1.1.0 or higher** the JSON representation is replaced by a java object structure with the same structure and naming.


## Current Limitation of Interfaces
The current implementation of the interfaces has the same limitations as the [Union](./union.md) implementation.
