#Generation of the Person and Car Dataset

The [SPARQL-Generator](https://ci.mines-stetienne.fr/sparql-generate/playground.html) was used to generate an turtle dataset from two datasets cars.json and persons.jason which wher both generated with [mockaroo](https://mockaroo.com/).

##Generation Query

```
PREFIX iter: <http://w3id.org/sparql-generate/iter/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX fun: <http://w3id.org/sparql-generate/fn/>
PREFIX ex: <http://example.org/>
PREFIX it: <http://www.influencetracker.com/ontology#>
  GENERATE {
     <http://example.org/{REPLACE("person_:",":", ?personID)}> a ex:Person;
    					ex:name ?personName;
    					ex:surname ?personSurname;
    					ex:age ?age.
    GENERATE{
    <http://example.org/{REPLACE("person_:",":", ?personID)}> ex:relatedWith <http://example.org/{REPLACE("person_:",":", ?rel)}>
    }
  ITERATOR iter:JSONPath( ?relatedWith, "$[*]", "$") AS ?bm1 ?rel
  WHERE{
    }.
  GENERATE{
    <http://example.org/{REPLACE("person_:",":", ?personID)}> ex:drives <http://example.org/{REPLACE("car_:",":", ?drivesCar)}>
    }
  ITERATOR iter:JSONPath( ?drives, "$[*]", "$") AS ?bm2 ?drivesCar
  WHERE{
    }.

  <http://example.org/{REPLACE("car_:",":", ?carID)}> a ex:car;
    		ex:model ?model.
  GENERATE{
    <http://example.org/{REPLACE("car_:",":", ?carID)}> ex:ownedBy <http://example.org/{REPLACE("person_:",":", ?pers)}>
    }
  ITERATOR iter:JSONPath( ?ownedBy, "$[*]", "$") AS ?bm3 ?pers
  WHERE{
    }.

}

ITERATOR iter:JSONPath( <http://cars.json>, "$[*]", "$.carID", "$.model", "$.ownedBy") AS ?car ?carID ?model ?ownedBy
ITERATOR iter:JSONPath( <http://persons.json>, "$[*]", "$.personID", "$.name", "$.surname", "$.age", "$.relatedWith", "$.drives") AS ?person ?personID ?personName ?personSurname ?personAge ?relatedWith ?drives


```
