# UGQL Mutations

UltraGraphQL generates for all object types defined in the UGQLS a **insert** and **delete** mutation field.
Through the arguments of these fields all fields of the corresponding object can be defined to write insertions or deletions based on the input.
The insertion action always applies the same translation and the performed delete action depends on the defined input.
The mutation actions are only performed against one service and the selection set of the mutation is executed against all defined services.

## Enable Mutations
In order to enable the mutations the mutation setting in the UGQL [configuration](./config.md) must be set to true and a mutation service must be defined.
The following example shows a possible configuration.
```json
{
  "name": "hgql-example-with-sparql",
  "schema": "gql/schema.graphql",
  "mutations": true,
  "mutationService": "dbpedia-sparql",
  "server": {
    "port": 8080,
    "graphql": "/graphql",
    "graphiql": "/graphiql"
  },
  "services": [
    {
      "id": "dbpedia-sparql",
      "type": "SPARQLEndpointService",
      "filepath": "data/dataset_8000.ttl",
      "fieltype": "TTL",
      "graph": "http://dbpedia.org"
    }
  ]
}
```

> The *mutationService* MUST be the id of a defined service in the *services* list.

## Insertions
Insertions are translated straight forward to SPARQL updates since the *_id* is mandatory the input is translated into triples and inserted into the service.
The following example shows a possible insert mutation.
```graphql
mutation{
    insert_ex_Person(_id: "https://example.org/Bob", ex_name: "Bob", ex_age: "42", ex_relatedWith: {_id: "https://example.org/Alice"}){
        _id
        ex_name
        ex_relatedWith{
            ex_name
        }
    }
}
```
The example insert mutation results in the following SPARQL update.
```sparql
PREFIX ex: <https://example.org/>
INSERT DATA{
    GRAPH <...>{
        ex:Bob a ex:Person;
            ex:name "Bob";
            ex:age "42";
            ex:relstedWith ex:Aliceb .
    }
}
```
> If no graph is defined for the service the *GRAPH* clause is left out

The selection set of the mutation is internally treated as the following GraphQL query and executed by the query handler.
```graphql
query{
    ex_Person{
        _id
        ex_name
        ex_relatedWith{
            ex_name
        }
    }
}
```

The final result of the mutation is than the result of the mutation concatenated with the result of the query.

## Deletions
Depending on the provided input the mutation action of the delete mutation alters.
In the following all possible delete actions are explained.

>NOTE: Deleting data may not result in the actual deletion of the information.
> In RDF information can be implied through inference rules meaning that the deletion of the actual data may not delete the inference of the same data through other information in the triple store.

### Delete by ID
If only the ID (IRI) of an resource is given than all information about this ID related to the object is deleted from the mutation service.
This means that only the information that are defined as fields in the object are deleted.
Data that is linked through another object is left out.
The following example therefore deletes all person data that includes the defined ID.
```graphql
mutation{
    delete_ex_Person(_id: "https://example.org/Bob"){
        _id
    }
}
```
Resulting in the following SPARQL update.
```sparql
PREFIX ex: <https://example.org/>
WITH <...>
DELETE{
    ?perosn ?p1 ?o .
    ?s ?p2 ?person .
}
WHERE{
    ?person a ex:Person .
    OPTIONAL{
        ?perosn ?p1 ?o .
    }
    OPTIONAL{
        ?s ?p2 ?person .
    }
}
```
>The *WITH* clause contains the graph information from the mutation service. If the graph is not defined than the clause is left out.

### Delete by Matching Data
Is the **_id** not defined but other input data is provided, than all data of this object type that matches the provided input is deleted.
The following example deletes all persons that have the name *Bob*, are *42* jears old and are related to *Alice*.
```graphql
mutation{
    delete_ex_Person(ex_name: "Bob", ex_age: "42", ex_relatedWith: {_id: "https://example.org/Alice"}){
        _id
        ex_name
        ex_relatedWith{
            ex_name
        }
    }
}
```
The example delete mutation results in the following SPARQL update.
```sparql
PREFIX ex: <https://example.org/>
WITH <...>
DELETE{
    ?perosn ?p1 ?o .
    ?s ?p2 ?person .
}
WHERE{
    ?person a ex:Person;
        ex:name "Bob";
        ex:age "42";
        ex:relatedWith ex:Alice .
    OPTIONAL{
        ?perosn ?p1 ?o .
    }
    OPTIONAL{
        ?s ?p2 ?person .
    }
}
```
>The *WITH* clause contains the graph information from the mutation service. If the graph is not defined than the clause is left out.

### Delete Data
If the **_id** and other input information are provided than only the provided input data is deleted, except the _id.
The triple that defines the _id as type of the object is not deleted since other data of this _id related to the object could still exist.
The following delete mutation deletes the provided name age and related with connection to Alice.
Deleting the relation to the object Alice does not delete the object Alice only the connection to the object form Bobs object.
```graphql
mutation{
    delete_ex_Person(_id: "https://example.org/Bob", ex_name: "Bob", ex_age: "42", ex_relatedWith: {_id: "https://example.org/Alice"}){
        _id
        ex_name
        ex_relatedWith{
            ex_name
        }
    }
}
```
Resulting in the following SPARQL update.
```sparql
PREFIX ex: <https://example.org/>
DELETE DATA{
    GRAPH <...>{
        ex:Bob a ex:Person;
            ex:name "Bob";
            ex:age "42";
            ex:relstedWith ex:Aliceb .
    }
}
```