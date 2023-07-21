package org.eeichinger.servicevirtualisation.jdbc;

import lombok.Setter;
import lombok.SneakyThrows;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Records called JDBC calls in the mapping json format that WireMock uses (e.g. expectedRecording.json)
 * These are saved sequentially in /test/resources/newMappings/, e.g. 1.json, 2.json. This directory should exist.
 * Workflow: these files can be renamed and move to the directory that WireMock is expecting (e.g. /test/resources/mappings)
 */
public class WireMockMappingJsonRecorder {

    @Setter
    private XmlTypeRegistry xmlTypeRegistry;

    public WireMockMappingJsonRecorder() {
        this(false);
    }

    public WireMockMappingJsonRecorder(boolean useExplicitTypes) {
        if (useExplicitTypes) {
            xmlTypeRegistry = new XmlTypeRegistry();
        }
    }

    private int statementInTest = 0;

    @SneakyThrows
    protected String getSybaseXMLFromDBResult(ResultSet resultSet) {
        StringBuilder parameterXml = new StringBuilder();
        parameterXml.append("<resultset xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'><cols>");
        ResultSetMetaData md = resultSet.getMetaData();
        int numCols = md.getColumnCount();
        List<String> colNames = IntStream.range(0, numCols)
            .mapToObj(i -> {
                try {
                    return md.getColumnName(i + 1);
                } catch (SQLException e) {
                    e.printStackTrace();
                    return "?";
                }
            }).collect(Collectors.toList());

        colNames.forEach(s -> parameterXml
            .append("<col>")
            .append(s)
            .append("</col>"));

        parameterXml.append("</cols>");

        while (resultSet.next()) {
            parameterXml.append("<row>");
            for (String cn : colNames) {
                Object rowValue = resultSet.getObject(cn);
                if (rowValue == null) {
                    parameterXml.append("<val xsi:nil='true'></val>");
                } else {
                    if (xmlTypeRegistry != null) {
                        String mappedType = xmlTypeRegistry.getXmlType(rowValue.getClass());
                        if (mappedType != null) {
                            parameterXml.append("<val xsi:type='").append(mappedType).append("'>").append(rowValue).append("</val>");
                        } else if (rowValue instanceof String) {
                            parameterXml.append("<val>").append(rowValue).append("</val>");
                        } else {
                            throw new RuntimeException("Didn't know how to write out " + rowValue + " of class " + rowValue.getClass());
                        }
                    } else { //always coerce to string
                        parameterXml.append("<val>").append(rowValue).append("</val>");
                    }
                }
            }
            parameterXml.append("</row>");
        }

        parameterXml.append("</resultset>");

        return parameterXml.toString();
    }

    protected String getRecordFilePath(int statementInTest) {
        return "src/test/resources/newMappings/" + statementInTest + ".json";
    }

    @SneakyThrows
    public void writeOutMapping(JdbcServiceVirtualizationFactory.PreparedStatementInformation preparedStatementInformation, ResultSet resultSet) {
        PrintWriter printWriter = new PrintWriter(new FileWriter(getRecordFilePath(++statementInTest)));
        printWriter.print("{\n" +
            "  \"request\": {\n" +
            "    \"method\": \"POST\",\n" +
            "    \"url\": \"/sqlstub\",\n" +
            "    \"bodyPatterns\":\n" +
            "            [\n" +
            "              {\n" +
            "                \"equalTo\": \"");
        printWriter.print(preparedStatementInformation.getSql());
        printWriter.print("\"\n" +
            "              }\n" +
            "            ],\n" +
            "    \"headers\": {\n" +
            "      ");

        String paramHeaders = preparedStatementInformation.getParameterValues().entrySet().stream().map(entry -> String.format(" \"%d\": {\n" +
            "        \"equalTo\": \"%s\"\n" +
            "      }", entry.getKey(), entry.getValue())).collect(Collectors.joining(",\n" +
            "      "));
        printWriter.print(paramHeaders);

        printWriter.print("}\n" +
            "  },\n" +
            "  \"response\": {\n" +
            "    \"status\": 200,\n" +
            "    \"body\": \"");

        printWriter.print(getSybaseXMLFromDBResult(resultSet));

        printWriter.print("\"\n" +
            "  }\n" +
            "}");

        printWriter.close();

    }
}
