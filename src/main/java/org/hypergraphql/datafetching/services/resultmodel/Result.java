package org.hypergraphql.datafetching.services.resultmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public abstract class Result <T>{

    ObjectMapper mapper = new ObjectMapper();

    String nodeId = null;
    String name = null;
    String alias;
    Map<String, Object> args;
    boolean isList = false;

    public String getErrors() {
        return errors;
    }

    String errors = "";

    public Result(String name) {
        this.name = name;
        this.alias = null;
        this.args = new HashMap<>();
    }

    public Result(String name, String alias) {
        this.name = name;
        this.alias = alias;
        this.args = new HashMap<>();
    }

    public Result(String name, Map<String, Object> args) {
        this.name = name;
        this.alias = null;
        this.args = args;
    }

    public Result(String name, String alias, Map<String, Object> args) {
        this.name = name;
        this.alias = alias;
        this.args = args;
    }

    public Boolean isList() {
        return isList;
    }

    public void isList(Boolean list) {
        isList = list;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public abstract T generateJSON();

    public abstract void merge(Result result);

}




