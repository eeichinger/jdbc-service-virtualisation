package org.eeichinger.servicevirtualisation.jdbc;

import java.sql.Date;
import java.time.LocalDate;

import com.mockrunner.mock.jdbc.MockResultSet;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

/**
 * @author Erich Eichinger
 * @since 12/07/16
 */
public class MockResultSetHelperTest {

    @Test
    public void can_parse_sybase_element_dialect() throws Exception {
        String xml = ""
            + "<resultset xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n"
            // nil='true' -> null value
            + "     <row><name>Erich Eichinger</name><birthday xsi:nil='true' /><x name='placeofbirth'>London</x></row>\n"
            // omitted element -> null value
            + "     <row><name>Matthias Bernlöhr</name><placeofbirth>Stuttgart</placeofbirth></row>\n"
            // empty element -> empty string
            + "     <row><name>Max Mustermann</name><birthday></birthday><placeofbirth>Berlin</placeofbirth></row>\n"
            // normal element -> whatever you make of it
            + "     <row><name>James Bond</name><birthday>1900-01-04</birthday><placeofbirth>Philadelphia</placeofbirth></row>\n"
            + "</resultset>\n";

        System.out.println(xml);

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(4));

        resultSet.next();
        assertThat(resultSet.getString(1), equalTo("Erich Eichinger"));
        assertThat(resultSet.getString(2), nullValue());
        assertThat(resultSet.getString(3), equalTo("London"));
        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("Matthias Bernlöhr"));
        assertThat(resultSet.getString("birthday"), nullValue());
        assertThat(resultSet.getString("placeofbirth"), equalTo("Stuttgart"));
        resultSet.next();
        assertThat(resultSet.getString(1), equalTo("Max Mustermann"));
        assertThat(resultSet.getString(2), equalTo(""));
        assertThat(resultSet.getString(3), equalTo("Berlin"));
        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("James Bond"));
        assertThat(resultSet.getDate("birthday"), equalTo(Date.valueOf(LocalDate.of(1900, 1, 4))));
        assertThat(resultSet.getString("placeofbirth"), equalTo("Philadelphia"));
    }

    @Test
    public void determine_column_names_from_cols_row() throws Exception {
        String xml = ""
            + "<resultset xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n"
            + "     <cols><col>name</col><col>birthday</col><col>placeofbirth</col></cols>\n"
            + "     <row><col>James Bond</col><col>1900-01-04</col><col>Philadelphia</col></row>\n"
            + "</resultset>\n";

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(1));

        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("James Bond"));
        assertThat(resultSet.getDate("birthday"), equalTo(Date.valueOf(LocalDate.of(1900, 1, 4))));
        assertThat(resultSet.getString("placeofbirth"), equalTo("Philadelphia"));
    }

    @Test
    public void determine_column_names_from_first_row_element_names_or_attribute() throws Exception {
        String xml = ""
            + "<resultset xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n"
            + "     <row><name>James Bond</name><col name='birthday' xsi:nil='true' /><placeofbirth>Philadelphia</placeofbirth></row>\n"
            + "</resultset>\n";

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(1));

        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("James Bond"));
        assertThat(resultSet.getDate("birthday"), nullValue());
        assertThat(resultSet.getString("placeofbirth"), equalTo("Philadelphia"));
    }

    @Test
    public void parse_nil_attribute_as_null() throws Exception {
        String xml = ""
            + "<resultset xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n"
            + "     <cols><col>name</col><col>birthday</col><col>placeofbirth</col></cols>\n"
            + "     <row><col>James Bond</col><col xsi:nil='true'/><col>Philadelphia</col></row>\n"
            + "</resultset>\n";

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(1));

        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("James Bond"));
        assertThat(resultSet.getDate("birthday"), nullValue());
        assertThat(resultSet.getString("placeofbirth"), equalTo("Philadelphia"));
    }

    @Test
    public void parse_empty_element_as_empty_string() throws Exception {
        String xml = ""
            + "<resultset>\n"
            + "     <cols><col>name</col><col>birthday</col><col>placeofbirth</col></cols>\n"
            + "     <row><col>James Bond</col><col /><col>Philadelphia</col></row>\n"
            + "</resultset>\n";

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(1));

        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("James Bond"));
        assertThat(resultSet.getString("birthday"), equalTo(""));
        assertThat(resultSet.getString("placeofbirth"), equalTo("Philadelphia"));
    }

    @Test
    public void parse_missing_named_element_as_null() throws Exception {
        String xml = ""
            + "<resultset>\n"
            + "     <cols><col>name</col><col>birthday</col><col>placeofbirth</col></cols>\n"
            + "     <row><name>James Bond</name><placeofbirth>Philadelphia</placeofbirth></row>\n"
            + "</resultset>\n";

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(1));

        resultSet.next();
        assertThat(resultSet.getString("name"), equalTo("James Bond"));
        assertThat(resultSet.getString("birthday"), nullValue());
        assertThat(resultSet.getString("placeofbirth"), equalTo("Philadelphia"));
    }

    @Test
    public void parse_columns_in_order_of_column_names() throws Exception {
        String xml = ""
            + "<resultset>\n"
            + "     <cols><col>name</col><col>birthday</col><col>placeofbirth</col></cols>\n"
            + "     <row><placeofbirth>Philadelphia</placeofbirth><name>James Bond</name></row>\n"
            + "</resultset>\n";

        final MockResultSet resultSet = new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", xml);

        assertThat(resultSet.getColumnCount(), equalTo(3));
        assertThat(resultSet.getRowCount(), equalTo(1));

        assertThat(resultSet.getMetaData().getColumnName(1), equalTo("name"));
        assertThat(resultSet.getMetaData().getColumnName(2), equalTo("birthday"));
        assertThat(resultSet.getMetaData().getColumnName(3), equalTo("placeofbirth"));

        resultSet.next();
        assertThat(resultSet.getString(1), equalTo("James Bond"));
        assertThat(resultSet.getString(2), nullValue());
        assertThat(resultSet.getString(3), equalTo("Philadelphia"));
    }
}
