# Schema Extraction Query
The query to extract the schema from SPARQL endpoints can be modified either by changing the query file in the [configuration file](./config.md) or by altering the [mapping](./schema_mapping.md).
By altering the mapping the HGQL instance automatically builds a corresponding query extracting the schema based on the defined mapping.
The query templating is realized with the Jena templating features which replaces variables in a given query.
The variables that are occupied by the templating are listed  [here](#template-variables) and MUST be used if the mapping should automatically be build into the query.
If the query should be static and not dynamically build up then these variables should not be used.

## Requirements
- The query MUST insert the extracted schema into the local graph as done in the example query.
- The service variable MUST be used to allow the HQGL instance to query different endpoints
- Extracted Schema MUST have a triple structure

## Template variables
The template variable names are based on the Default Mapping Vocabulary because these names are closer to the RDF context.

|variable|Description|
| --- | --- |
|?service|Represents a SPARQL endpoint service as URL|
|?class|ONE Node that represents a HGQL object|
|?classes|ALL Nodes that represent a HGQL object|
|?property|One node that represents a HGQL field|
|?properties|ALL nodes that represent a HGQL field|
|?domain|One node that represents the domain of a field|
|?domains|ALL nodes that represent the domain of a field|
|?range|ONE node that represents the output type of a field|
|?ranges|ALL nodes that represent the output type of a field|
|?subClassOf|ONE node that represents if a object implements another object|
|?subClassesOf|ALL nodes that represent  if a object implements another object|
|?subPropertyOf|ONE node that represents if a field implies another field|
|?subPropertiesOf|ALL nodes that represent if a field implies another field|
|?equivalentClass|ONE node that represents if a fields mutually implement each other|
|?equivalentClasses|ALL nodes that represent if a fields mutually implement each other|
|?equivalentProperty|ONE node that represents if fields share the same output type|
|?equivalentProperties|ALL node that represent if fields share the same output type|
|?sameAs|ONE node that represents if some fields or types are the same|
|?sameAses|ALL nodes that represent if some fields or types are the same|
|?graph| Graph from the configuration file defined for the service WITH the GRAPH keyword |
|?from| Graph from the configuration file defined for the service WITH the FROM keyword |

>**Note**: The SPARQL Query templating injects the mappings that correspond to the defined variables. This means that the
>mapping files and services URLs are vulnerable to query injection attacks.

>In the assumption that property path operators, like ^ for the inverse path, are distributive the variables that represent all mappings must be surrounded by brackets.


## Example Schema Extraction Query Using Templating
#### Template Query
```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

# Delete any leftovers of previous schemata
#DROP GRAPH <http://example.org/LOVInferred>;

# Find all predicates used in the current dataset and their equivalents and generalizations
INSERT {
  ?predicate a ?property;
    ?domain ?pDomain;
    ?range ?pRange.
}
WHERE {
  {
    SERVICE ?service{
        {SELECT DISTINCT ?x ?pDomain ?pRange {
          ?s ?x ?o.
          OPTIONAL { ?s a ?pDomain}
          OPTIONAL { ?o a ?pRange}
        }}
        ?x (?subPropertiesOf|?equivalentProperties|^(?equivalentProperties)|?sameAses|^(?sameAses))* ?predicate FILTER(!isBlank(?predicate))
    }
  }
};

# Find all classes used in the current dataset or implied through entailment
INSERT {
  ?y a ?class
}
WHERE {
  {
    SELECT DISTINCT ?y {
        SERVICE ?service {
            [] a ?y FILTER(!isBlank(?y))
        }
    }
  } UNION {
    {SELECT DISTINCT ?y {
      ?predicate a ?property
      SERVICE ?service{
        ?predicate (?ranges|?domains) ?y FILTER(!isBlank(?y))
      }
    }}
  }
};

# Find all class equivalences implied through entailment
INSERT {
  ?concept a ?class
}
WHERE {
  {
    ?y a ?class
    SERVICE ?service{
        ?y (?subClassesOf|?sameAses|^(?sameAses)|?equivalentClasses|^(?equivalentClasses))+ ?concept FILTER(!isBlank(?concept))
    }
  }
};


# Find describing information of previously discovered classes and predicates
INSERT {
  ?s ?p ?o
}
WHERE {
  {
    {?s a ?property} UNION {?s a ?class}
    SERVICE ?service{
        ?s ?p ?o
    }
  }
};
```

#### Resulting Query
- Mapping: default mapping
- Service url: <TESTService_2:
```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

# Delete any leftovers of previous schemata
#DROP GRAPH <http://example.org/LOVInferred>;

# Find all predicates used in the current dataset and their equivalents and generalizations
INSERT {
  ?predicate a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>;
    <http://www.w3.org/2000/01/rdf-schema#domain> ?pDomain;
    <http://www.w3.org/2000/01/rdf-schema#range> ?pRange.
}
WHERE {
  {
    SERVICE <TESTService_2>{
        {SELECT DISTINCT ?x ?pDomain ?pRange {
          ?s ?x ?o.
          OPTIONAL { ?s a ?pDomain}
          OPTIONAL { ?o a ?pRange}
        }}
        ?x (<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>|<http://www.w3.org/2002/07/owl#equivalentProperty>|^(<http://www.w3.org/2002/07/owl#equivalentProperty>)|<http://www.w3.org/2002/07/owl#sameAs>|^(<http://www.w3.org/2002/07/owl#sameAs>))* ?predicate FILTER(!isBlank(?predicate))
    }
  }
};

# Find all classes used in the current dataset or implied through entailment
INSERT {
  ?y a <http://www.w3.org/2000/01/rdf-schema#Class>
}
WHERE {
  {
    SELECT DISTINCT ?y {
        SERVICE <TESTService_2> {
            [] a ?y FILTER(!isBlank(?y))
        }
    }
  } UNION {
    {SELECT DISTINCT ?y {
      ?predicate a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>
      SERVICE <TESTService_2>{
        ?predicate (<http://www.w3.org/2000/01/rdf-schema#range>|<http://www.w3.org/2000/01/rdf-schema#domain>) ?y FILTER(!isBlank(?y))
      }
    }}
  }
};

# Find all class equivalences implied through entailment
INSERT {
  ?concept a <http://www.w3.org/2000/01/rdf-schema#Class>
}
WHERE {
  {
    ?y a <http://www.w3.org/2000/01/rdf-schema#Class>
    SERVICE <TESTService_2>{
        ?y (<http://www.w3.org/2000/01/rdf-schema#subClassOf>|<http://www.w3.org/2002/07/owl#sameAs>|^(<http://www.w3.org/2002/07/owl#sameAs>)|<http://www.w3.org/2002/07/owl#equivalentClass>|^(<http://www.w3.org/2002/07/owl#equivalentClass>))+ ?concept FILTER(!isBlank(?concept))
    }
  }
};


# Find describing information of previously discovered classes and predicates
INSERT {
  ?s ?p ?o
}
WHERE {
  {
    {?s a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>} UNION {?s a <http://www.w3.org/2000/01/rdf-schema#Class>}
    SERVICE <TESTService_2>{
        ?s ?p ?o
    }
  }
};

```