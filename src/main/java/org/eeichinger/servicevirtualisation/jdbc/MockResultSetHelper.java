package org.eeichinger.servicevirtualisation.jdbc;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mockrunner.base.NestedApplicationException;
import com.mockrunner.mock.jdbc.MockResultSet;
import lombok.Setter;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

/**
 * @author Erich Eichinger
 * @since 25/06/2016
 */
public class MockResultSetHelper {

    @Setter
    private XmlTypeRegistry xmlTypeRegistry = new XmlTypeRegistry();

    /**
     * Parse a MockResultSet from the provided Sybase-style formatted XML Document.
     * See <a href="http://dcx.sybase.com/1200/en/dbusage/xmldraftchapter-s-3468454.html">Sybase - Using FOR XML RAW</a> documentation and
     * <a href="http://dcx.sybase.com/1200/en/dbusage/forxml-null.html">FOR XML and NULL values</a> for handling NULL values.
     * <p>
     * Note: Currently only the ELEMENT form of the format is supported (see example below)!
     * <p>
     * Example XML:
     * <pre>{@code
     * <resultset xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>
     *    <row><name>Erich Eichinger</name><birthday xsi:nil='true' /><placeofbirth>London</placeofbirth></row>
     *    <row><name>Matthias Bernlöhr</name><placeofbirth>Stuttgart</placeofbirth></row>
     *    <row><name>Max Mustermann</name><birthday></birthday><placeofbirth>Berlin</placeofbirth></row>
     *    <row><name>James Bond</name><birthday>1900-01-04</birthday><placeofbirth>Philadelphia</placeofbirth></row>
     * </resultset>
     * }</pre>
     */
    @SuppressWarnings({"unchecked"})
    public MockResultSet parseResultSetFromSybaseXmlString(String id, String xml) {
        MockResultSet resultSet = new MockResultSet(id);
        SAXBuilder builder = new SAXBuilder();
        Document doc;

        try {
            doc = builder.build(new StringReader(xml));

            List<String> columnNames = parseColumnNames(resultSet, doc.getRootElement());

            Element root = doc.getRootElement();
            List<Element> rows = root.getChildren("row");
            for (Element currentRow : rows) {
                List<Element> columns = (List<Element>) currentRow.getChildren();
                DatabaseRow rowValues = new DatabaseRow(columnNames);
                for (Element col : columns) {
                    rowValues.add(col);
                }
                resultSet.addRow(rowValues.toRowValues());
            }
        } catch (Exception exc) {
            throw new NestedApplicationException("Failure while reading from XML file", exc);
        }
        return resultSet;
    }

    protected class DatabaseRow {
        final List<String> colNames;
        final Map<String, Object> namedValues;
        final Object[] positionalValues;

        int colCount;

        public DatabaseRow(List<String> colNames) {
            this.colNames = colNames;
            this.colCount = 0;
            this.namedValues = new HashMap<>();
            this.positionalValues = new Object[this.colNames.size()];
        }

        public void add(Element col) {
            String name = getElementName(col);
            Object val = getNilableElementText(col);
            if (colNames.contains(name)) {
                namedValues.put(name, val);
            } else {
                positionalValues[colCount] = val;
            }
            colCount++;
        }

        public List<Object> toRowValues() {
            List<Object> vals = new ArrayList<>(this.colNames.size());
            for(int i=0;i<colNames.size();i++) {
                String colName = colNames.get(i);
                Object colValue;
                if (namedValues.containsKey(colName)) {
                    colValue = namedValues.get(colName);
                } else {
                    colValue = positionalValues[i];
                }
                vals.add(colValue);
            }
            return vals;
        }
    }

    protected Object getNilableElementText(Element col) {
        String val = col.getText();
        if ("true".equalsIgnoreCase(col.getAttributeValue("nil", nsXsi))) {
            return null;
        }
        else {
            String type = col.getAttributeValue("type", nsXsi);
            if (type != null) {
                return xmlTypeRegistry.parseValue(type, val);
            }
        }
        return val;
    }

    protected List<String> parseColumnNames(MockResultSet resultSet, Element root) {
        // determine columns
        Element colsHeaderRow = root.getChild("cols");
        List<String> colNames = new ArrayList<>();
        if (colsHeaderRow != null) {
            for(Element col : cast(colsHeaderRow.getChildren("col"), Element.class)) {
                final String colName = col.getText();
                resultSet.addColumn(colName);
                colNames.add(colName);
            }
        } else {
            Element headerRow = cast(root.getChildren("row"), Element.class).get(0);
            for(Element col : cast(headerRow.getChildren(), Element.class)) {
                String colName = getElementName(col);
                resultSet.addColumn(colName);
                colNames.add(colName);
            }
        }
        return Collections.unmodifiableList(colNames);
    }

    protected String getElementName(Element col) {
        String name = col.getAttributeValue("name");
        if (name == null) {
            name = col.getName();
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(List<?> list, Class<T> elementType) {
        return (List<T>) list;
    }

    private static final Namespace nsXsi = Namespace.getNamespace("http://www.w3.org/2001/XMLSchema-instance");
}
