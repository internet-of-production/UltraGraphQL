package org.hypergraphql.query.pattern;

import java.util.List;
import java.util.Map;

public class QueryPatternBuilder {
    private String name;
    private String alias;
    private String nodeId;
    private Map<String, Object> args;
    private String targetType;
    private SubQueriesPattern fields;
    private String parentName;
    private String parentId;
    private String parentAlias;
    private Map<String, Object> parentArgs;
    private String parentType;


    public QueryPatternBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public QueryPatternBuilder setAlias(String alias) {
        this.alias = alias;
        return this;
    }

    public QueryPatternBuilder setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public QueryPatternBuilder setArgs(Map<String, Object>  args) {
        this.args = args;
        return this;
    }

    public QueryPatternBuilder setTargetType(String targetType) {
        this.targetType = targetType;
        return this;
    }

    public QueryPatternBuilder setFields(SubQueriesPattern fields) {
        this.fields = fields;
        return this;
    }

    public QueryPatternBuilder setParentName(String parentName) {
        this.parentName = parentName;
        return this;
    }

    public QueryPatternBuilder setParentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public QueryPatternBuilder setParentAlias(String parentAlias) {
        this.parentAlias = parentAlias;
        return this;
    }

    public QueryPatternBuilder setParentArgs(Map<String, Object> parentArgs) {
        this.parentArgs = parentArgs;
        return this;
    }

    public QueryPatternBuilder setParentType(String parentType) {
        this.parentType = parentType;
        return this;
    }

    public QueryPattern createQueryPattern() {
        return new QueryPattern(name, alias, nodeId, args, targetType, fields, parentName, parentId, parentAlias, parentArgs, parentType);
    }
}