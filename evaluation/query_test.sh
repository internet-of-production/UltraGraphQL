for run in {1..10}
do
  curl --location --request POST 'localhost:8098/graphql' \
--header 'Content-Type: application/json' \
--data-raw '{"query":"{\n  ex_Person{\n    _id\n    ex_relatedWith(_id:[\"http://example.org/person_489\"]){\n      _id\n      ex_relatedWith(_id:[\"http://example.org/person_559\"]){\n        _id\n        ex_relatedWith(_id:[\"http://example.org/person_292\"]){\n          _id\n          ex_relatedWith{\n            _id\n\n          }\n        }\n      }\n    }\n  }\n}","variables":{}}' 
done
