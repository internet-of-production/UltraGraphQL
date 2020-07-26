# UGQL Mutations

# TODO: Explain Mutations

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
```graphql
mutation{
    delete_ex_Person(_id: "https://example.org/Bob"){
        _id
    }
}
```
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