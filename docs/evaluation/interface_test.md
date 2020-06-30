# Test of the interface feature of HGQL
This test is preformed [here](../../src/test/java/org/hypergraphql/ApplicationTest.java) in the interfaceTest() method.
Information on how unions were added to HGQL can be found [here](../interface.md)

## Test Setup
A HGQL Schema is provided with an interface defined as OutputType. Two types implement this interface and one type has type specific fields that the other type does NOT have.
The [query](#graphql-query) queries fields of the interface and with an InlineFragment also type specific types.

## Expected Outcome
The query schoud return data of both types and for the type were the type specific field was queried also data about this field.

### HGQL Schema
```GraphQL
type __Context{
    lastName:	_@href(iri:"http://www.example.org/lastName")
    firstName:	_@href(iri:"http://www.example.org/firstName")
    owner:	_@href(iri:"http://www.example.org/owner")
    name:	_@href(iri:"http://www.example.org/name")
    age:	_@href(iri:"http://www.example.org/age")
    dog:	_@href(iri:"http://www.example.org/dog")
    cat:	_@href(iri:"http://www.example.org/cat")
    pet:	_@href(iri:"http://www.example.org/pet")
    Person:	_@href(iri:"http://www.example.org/Person")
    color:	_@href(iri:"http://www.example.org/color")
}


interface animal{
    name: [String] @service(id: "dataset")
    age: [String] @service(id: "dataset")
}

interface blank{

}

type dog implements animal @service(id: "dataset"){
    name: [String] @service(id: "dataset")
    age: [String] @service(id: "dataset")
    color: [String] @service(id: "dataset")
}

type cat implements animal @service(id: "dataset"){
    name: [String] @service(id: "dataset")
    age: [String] @service(id: "dataset")
}

type Person implements blank @service(id: "dataset"){
    firstName: [String] @service(id: "dataset")
    lastName: [String] @service(id: "dataset")
    pet: [animal] @service(id: "dataset")
}
```

### GraphQL Query
```GraphQL
{
  Person{
    _id
    firstName
    pet{
      name
      ...on Dog{
        color
      }
    }
  }
}
```

### SPARQL Query
```sparql
SELECT *
WHERE {
  {
    SELECT ?x_1
    WHERE {
      ?x_1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.example.org/Person> .
    }
  }
  OPTIONAL {
    ?x_1 <http://www.example.org/pet> ?x_1_1_y_1 .
    ?x_1_1_y_1 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.example.org/cat> .
     OPTIONAL {
       ?x_1_1_y_1 <http://www.example.org/name> ?x_1_1_y_1_1 .
     }
   }
   OPTIONAL {
     ?x_1 <http://www.example.org/pet> ?x_1_1_y_2 .
     ?x_1_1_y_2 <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.example.org/dog> .
     OPTIONAL {
       ?x_1_1_y_2 <http://www.example.org/color> ?x_1_1_y_2_1 .
     }
     OPTIONAL { ?x_1_1_y_2 <http://www.example.org/name> ?x_1_1_y_2_2 .
     }
   }
   OPTIONAL {
     ?x_1 <http://www.example.org/firstName> ?x_1_2 .
   }
 }
```

### GraphQL Result
```GraphQL
{
   "extensions":{

   },
   "data":{
      "Person":[
         {
            "firstName":[
               "Alice"
            ],
            "pet":[
               {
                  "color":[
                     "brown"
                  ],
                  "name":[
                     "Santa's Little Helper"
                  ],
                  "_id":"http://www.example.org/dog_a"
               },
               {
                  "name":[
                     "Snowball"
                  ],
                  "_id":"http://www.example.org/cat_a"
               }
            ]
         }
      ],
      "@context":{
         "firstName":"http://www.example.org/firstName",
         "color":"http://www.example.org/color",
         "_type":"@type",
         "name":"http://www.example.org/name",
         "_id":"@id",
         "Person":"http://hypergraphql.org/query/Person",
         "pet":"http://www.example.org/pet"
      }
   },
   "errors":[

   ]
}

```
