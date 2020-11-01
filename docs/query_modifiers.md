# Supported Query Modifiers
|  modifier | supported by |
|-----------|--------------| 
| _id | query roots and any field that ahas a type as output |
| lang | fields with String as output |
| limit | fields with an array as output type |
| offset | fields with an array as output type |
| order | fields with an array as output type |

## _id
Allows to define a set of IRIs to query fo specific objects.
If only one ID is defined a String instead of an array acn be used.

Example:
```graphql
{
    Person(_id: "http://www.example.org/alice"){
        label
        friends(_id: ["http://www.example.org/bob","http://www.example.org/Rust"]
    }
}
```

## lang
Allows to query for a literal for a specific language.
Only one language can be defined per query, to query multiple languages the alias feature can be used.

Example:
```graphql
{
  Person{
    label(lang:"en")
    friends{
      de: label(lang:"de")
      fr: label(lang:"fr")
    }
  }
}
```

## limit and offset
Limits the amount of resulting objects in a array and offset defines the start of the objects.
Both features can be used for pagination features.
If the order of the results is random the [order](#order) SHOULD be defined to ensure constant results.

Example:
```graphql
{
  Person{
    label
    friends(limit: 1 offset:10){
        label
        friends(limit:1)
    }
  }
}
```

## order
With the order field the rsult array can be ordered *DESCENDING* and *ASCENDING*.

Example:
```graphql
{
  Person(order:ASCENDING){
    label
    friends(order:DESCENDING){
      _id
    }
  }
}
```