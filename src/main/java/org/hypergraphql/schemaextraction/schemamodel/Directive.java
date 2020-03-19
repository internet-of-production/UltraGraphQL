package org.hypergraphql.schemaextraction.schemamodel;

import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

public class Directive {
    private String name;
    private Map<String, Parameter> parameter = new HashMap<>();
    // Boolean values of GraphQL
    private final String TRUE = "true";
    private final String FALSE = "false";

    public Directive(String name){
        this.name = name;
    }

    public Directive(Directive clone){
        this.name = clone.name;
        clone.parameter.values().forEach(parameter1 -> {
            if(parameter1 instanceof DirectiveParameter){
                this.parameter.put(parameter1.getName(), new DirectiveParameter((DirectiveParameter) parameter1));
            }else if(parameter1 instanceof DirectiveParameterList){
                this.parameter.put(parameter1.getName(), new DirectiveParameterList((DirectiveParameterList) parameter1));
            }
        });
    }

    public String getName() {
        return name;
    }

    public Map<String, Parameter> getParameter() {
        return parameter;
    }

    /**
     * Generates the SDL representation of this directive including all parameters.
     * @return SDL representation of this directive
     */
    public String build() {

        return String.format("@%s(%s)", name, buildParameters());
    }

    /**
     * Builds a SDL representation of the parameters of this directive. The different parameters are separated with a comma.
     * @return SDL representation of the parameters of this directive.
     */
    private String buildParameters(){
        return this.parameter.values().stream()
                .map(Parameter::build)
                .collect(Collectors.joining(", "));
    }

    /**
     * Add a parameter to this directive. The value of this parameter is a String.
     * If the parameter already exists then change the parameter to a list and add the value.
     * @param name Name of the parameter
     * @param value Single value of the parameter
     */
    public void addParameter(String name, String value){
        Parameter parameter = this.parameter.get(name);
        if(parameter == null){
            this.parameter.put(name, new DirectiveParameter(name, value));
        }else{
            if(parameter instanceof DirectiveParameter){
                if(!value.equals(((DirectiveParameter)this.parameter.get(name)).getValue())){
                    Set<String> values = new HashSet<String>(Arrays.asList(value, ((DirectiveParameter) parameter).getValue()));
                    this.parameter.replace(name, new DirectiveParameterList(name, values));
                }
            }else if(parameter instanceof DirectiveParameterList){
                ((DirectiveParameterList) parameter).addValue(value);
            }
        }

    }

    /**
     * Add a parameter with a list as value to this directive.
     * @param name Name of the parameter
     * @param values Set of values
     */
    public void addParameter(String name, Set<String> values){
        Parameter parameter = this.parameter.get(name);
        if(parameter == null){
            this.parameter.put(name, new DirectiveParameterList(name, values));
        }else{
            if(parameter instanceof DirectiveParameter){
                values.add(((DirectiveParameter) parameter).getValue());
                this.parameter.replace(name, new DirectiveParameterList(name, values));
            }else if(parameter instanceof DirectiveParameterList){
                ((DirectiveParameterList) parameter).addValues(values);
            }
        }
    }

    /**
     * Defines the required features of a directive parameter and default functions.
     */
    abstract class Parameter{

        /**
         * Retruns the name of this parameter
         * @return name of the parameter
         */
        abstract String getName();

        /**
         * Build a SDL representation of this parameter.
         * @return SDL representation of this parameter.
         */
        abstract String build();

        /**
         * Builds the corresponding SDL representation of the given value. Strings are returned in quotations and numerical
         * values and booleans without quotations.
         * @param value parameter value
         * @return SDL representation of the given value
         */
        String buildValue(String value){
            if(isStringNumeric(value)){
                return value;
            }
            else if(value.equals(TRUE) || value.equals(FALSE)){
                return value;
            }
            else{
                return String.format("\"%s\"", value);
            }
        }
    }

    /**
     * Represents a parameter that only has one value. Allowed value types are String, int, boolean (must be given in String)
     */
    class DirectiveParameter extends Parameter{
        private String name;
        private String value;

        public DirectiveParameter(String name, String value){
            this.name = name;
            this.value = value;
        }

        public DirectiveParameter(DirectiveParameter param){
            this.name = param.name;
            this.value = param.value;
        }

        public String getValue(){
            return this.value;
        }


        @Override
        String getName() {
            return name;
        }

        @Override
        public String build() {
            return String.format("%s: %s", name, buildValue(value));
        }

    }

    /**
     * Represents a parameter with a List as value.
     */
    class DirectiveParameterList extends Parameter{
        private String name;
        private Set<String> values;
        public DirectiveParameterList(String name ){
            this.name = name;
            this.values = new HashSet<>();
        }
        public DirectiveParameterList(String name, Set<String> values){
            this.name = name;
            this.values = values;
        }

        public DirectiveParameterList(DirectiveParameterList param) {
            this.name = param.name;
            this.values = new HashSet<>(param.values);
        }

        public void addValue(String value){
            this.values.add(value);
        }
        public void addValues(Set<String> values){
            this.values.addAll(values);
        }

        public Set<String> getValues() {
            return values;
        }

        @Override
        String getName() {
            return name;
        }

        @Override
        public String build() {
            return String.format("%s: [%s]", this.name, buildValues());
        }
        private String buildValues(){
            return this.values.stream()
                    .map(this::buildValue)
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * Checks if the given string is a numerical value
     * @param str Value to check if numeric
     * @return True if given value is numeric otherwise false
     * @auther https://stackoverflow.com/a/7092110
     */
    private static boolean isStringNumeric( String str )
    {
        DecimalFormatSymbols currentLocaleSymbols = DecimalFormatSymbols.getInstance();
        char localeMinusSign = currentLocaleSymbols.getMinusSign();

        if ( !Character.isDigit( str.charAt( 0 ) ) && str.charAt( 0 ) != localeMinusSign ) return false;

        boolean isDecimalSeparatorFound = false;
        char localeDecimalSeparator = currentLocaleSymbols.getDecimalSeparator();

        for ( char c : str.substring( 1 ).toCharArray() )
        {
            if ( !Character.isDigit( c ) )
            {
                if ( c == localeDecimalSeparator && !isDecimalSeparatorFound )
                {
                    isDecimalSeparatorFound = true;
                    continue;
                }
                return false;
            }
        }
        return true;
    }
}


