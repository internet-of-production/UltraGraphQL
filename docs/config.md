# HGQL Configuration File
## name
Defines the name of the HypergraphQL Endpoint.
> Datatype: String

## schema
Name of the schema file. If the extraction property is true then the extracted schema is saved in this file.
If NO schema file is provided then the schema extraction still functions but the schema will not be saved in a file.
> Datatype: String

##mutations
True if mutations fields SHOULD be generated with corresponding actions, otherwise false to permit mutations on this endpoint.
> Datatype: Boolean

##mutationService
Service id of the service on which the mutation actions are executed. The service id MUST correspond to a service that is defined in the services.
> Datatype: String

## extraction
True if the schema MUST be extracted from the given services otherwise use the provided schema file.
If this key is missing then it is assumed to be False.
> Datatype: Boolean

## mapping
Name of the mapping file. The Mapping defines how the RDF Schema is mapped to the GraphQL schema.
The syntax of the mapping file is described [here](./schema_mapping.md).
>Datatype: String

>File datatype: Turtle

## query
Name of the schema extraction query file. How the query must be written is defined [here](./schema_extraction_query.md)
>Datatype: String
-------
## server
The server object holds information about the HGQL server endpoint like port and URL paths.
### port
Port under which the HGQL endpoint is accessible.
>Datatype: INT

### graphql
URL path to reach the GraphQL service.
> Datatype: String (URL path)

### graphiql
URL path to reach the [GraphiQL](https://github.com/graphql/graphiql) service.
> Datatype: String (URL path)

### framework
Defines with which server frame work the HGQL endpoint is started. The following options are provided:
- spark: HGQL is provided over an SPARK server. Current findings indicate that this framework does not allow to run multiple HGQL instances on one system.
- jaxrs: HGQL is provided over the Apache CXF framework.

> Default: If the framework is not specified spark is chosen
> Datatype: String

-----------------------
## services
Contains a list of individual services.

>Datatype: JSON-list


### Service
A Service object consists of the id, type and URL of the service.
Additionally, the graph in which the data is stored and authentication information to access this service are given.
>Datatype: JSON-object
#### id
The id of a service MUST be unique for this service and will be used to link schema entities to a corresponding service using the **@service** directive.
#### type
The type defines of which type the service is.
The name of the type MUST correspond to a service java class.
Supported Services:
 - SPARQLEndpointService:   (remote) SPARQL endpoints
 - LocalModelSPARQLService: local RDF files
 - HGraphQLService: HyperGraphQL instance
>Datatype: String - MUST be one of the supported services listed above
#### url
The url key is only needed for the services *SPARQLEndpointService* or *HGraphQLService* is used.
Defines where the service can be accessed.
>Datatype: String (URL)
#### filepath
Only if the service is a *LocalModelSPARQLService*. Path to the dataset.
>Datatype: String
#### filetype
Only if the service is a *LocalModelSPARQLService*. File type of the dataset.
>Datatype: String
#### graph
Specifies in which graph of the data (If the data is stored in Quads).
>Dataset: String
#### user
Username to access the service
>Datatype: String
#### password
Password to access the service
>Datatype: String

#### exclude_from_extraction
If set to true the service will be ignored during the [extraction phase](./bootstrapping.md), but can be used during the [translation phase](./translation_phase.md).

>Datatype: Boolean
> 
> Default value: false

------------------------
##prefixes
Allows defining prefixes for namespaces that will be used for the name generation of the bootstrapping phase.
Normally the prefixes are queried form the web or are generated if non is found. To control the used prefixes they can be
defined here. Prefixes that are defined in this configuration overwrite any internal prefix look-up or generation.
>Note: namespace and prefix MUST be unique
The prefixes must be defined as follows:

```json
"prefixes":{
    "ex": "http//example.org/",
    "schema": "http://schema.org/"
}
``` 

> The use  of the IRI "http://hypergraphql.org/schema/" and the prefix "hgqls" are permitted as they are used for internal
> schema relations and auxiliary structures. If they are defined anyway, they are filtered out.

## Example configuration
### Configuration with extraction and SPARQLEndpointService
```json
{
  "name": "hgql-example-with-sparql",
  "schema": "gql/schema.graphql",
  "extraction": true,
  "mapping": "mapping.ttl",
  "query": "query.sparql",
  "server": {
    "port": 8080,
    "graphql": "/graphql",
    "graphiql": "/graphiql"
  },
  "services": [
    {
      "id": "dbpedia-sparql",
      "type": "SPARQLEndpointService",
      "url": "http://dbpedia.org/sparql/",
      "graph": "http://dbpedia.org",
      "user": "admin",
      "password": "admin"
    }
  ],
  "prefixes":{
      "ex": "http//example.org/",
      "schema": "http://schema.org/"
  }
}
```

### Configuration with predefined schema and a local RDF Dataset
```json
{
  "name": "hgql-example-with-sparql",
  "schema": "gql/schema.graphql",
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
