package org.hypergraphql.query.pattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryPattern implements Query{

    public final String name;
    public final String alias;
    public final String nodeId;
    public final Map<String, Object> args;
    public final String targetType;
    public SubQueriesPattern fields;
    public final String parentName;
    public final String parentId;
    public final String parentAlias;
    public final Map<String, Object> parentArgs;
    public final String parentType;

    public QueryPattern(String name,
                        String alias,
                        String nodeId,
                        Map<String, Object>  args,
                        String targetType,
                        SubQueriesPattern fields,
                        String parentName,
                        String parentId,
                        String parentAlias,
                        Map<String, Object> parentArgs,
                        String parentType) {
        this.name = name;
        this.alias = alias;
        this.nodeId = nodeId;
        this.args = args == null ? new HashMap<>(): args;
        this.targetType = targetType;
        this.fields = fields;
        this.parentName = parentName;
        this.parentId = parentId;
        this.parentAlias = parentAlias;
        this.parentArgs = parentArgs;
        this.parentType = parentType;
    }


    @Override
    public String toString() {
        return "QueryPattern{" +
                "name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", args=" + args +
                ", targetType='" + targetType + '\'' +
                ", fields=" + fields +
                ", parentName='" + parentName + '\'' +
                ", parentId='" + parentId + '\'' +
                ", parentAlias='" + parentAlias + '\'' +
                ", parentArgs='" + parentArgs + '\'' +
                ", parentType='" + parentType + '\'' +
                '}';
    }

    @Override
    public boolean isSubQuery() {
        return false;
    }
}
