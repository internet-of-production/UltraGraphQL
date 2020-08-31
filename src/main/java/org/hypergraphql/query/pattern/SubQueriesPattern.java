package org.hypergraphql.query.pattern;

import java.util.ArrayList;
import java.util.List;

public class SubQueriesPattern implements Query {

    public List<QueryPattern> subqueries;

    public SubQueriesPattern(){

    }

    public List<QueryPattern> getSubqueries() {
        return subqueries;
    }

    public void add(QueryPattern subquery){
        if(subquery != null){
            if(this.subqueries == null){
                this.subqueries = new ArrayList<>();
            }
            this.subqueries.add(subquery);
        }
    }

    public void addAll(List<QueryPattern> subqueries){
        if(this.subqueries == null){
            this.subqueries = new ArrayList<>();
        }
        this.subqueries.addAll(subqueries);
    }

    public void addAll(SubQueriesPattern subqueries){
        addAll(subqueries.getSubqueries());
    }

    @Override
    public boolean isSubQuery() {
        return true;
    }
}
