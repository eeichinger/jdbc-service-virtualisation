package org.eeichinger.servicevirtualisation.jdbc;

import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import com.mockrunner.base.NestedApplicationException;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * @author Erich Eichinger
 * @since 25/06/2016
 */
public class MockResultSetHelper {

    /**
     * Parse a MockResultSet from the provided Sybase-style formatted XML Document.
     * <p/>
     * Example XML:
     * <pre>{@code
     * <resultset>
     *      <cols><col>birthday</col><col>placeofbirth</col></cols>
     *      <row>
     *          <birthday>1980-01-01</birthday>
     *          <placeofbirth>Vienna</placeofbirth>
     *      </row>
     * </resultset>
     * }</pre>
     */
    @SuppressWarnings("rawtypes")
    public static MockResultSet parseResultSetFromSybaseXmlString(boolean trim, String id, String xml) {
        MockResultSet resultSet = new MockResultSet(id);
        SAXBuilder builder = new SAXBuilder();
        Document doc;

        try {
            doc = builder.build(new StringReader(xml));
            Element root = doc.getRootElement();
            List rows = root.getChildren("row");
            Iterator ri = rows.iterator();
            boolean firstIteration = true;
            int colNum = 0;
            while (ri.hasNext()) {
                Element cRow = (Element) ri.next();
                List cRowChildren = cRow.getChildren();
                Iterator cri = cRowChildren.iterator();
                if (firstIteration) {
                    List columns = cRowChildren;
                    Iterator ci = columns.iterator();

                    while (ci.hasNext()) {
                        Element ccRow = (Element) ci.next();
                        resultSet.addColumn(ccRow.getName());
                        colNum++;
                    }
                    firstIteration = false;
                }
                String[] cRowValues = new String[colNum];
                int curCol = 0;
                while (cri.hasNext()) {
                    Element crValue = (Element) cri.next();
                    String value = trim ? crValue.getTextTrim() : crValue.getText();
                    cRowValues[curCol] = value;
                    curCol++;
                }
                resultSet.addRow(cRowValues);
            }
        } catch (Exception exc) {
            throw new NestedApplicationException("Failure while reading from XML file", exc);
        }
        return resultSet;
    }
}
