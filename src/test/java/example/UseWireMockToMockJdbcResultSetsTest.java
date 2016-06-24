package example;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.Value;
import org.eeichinger.servicevirtualisation.jdbc.JdbcServiceVirtualizationFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Demonstrates you to use the technique to just spy on a real database and intercept/mock only selected jdbc queries
 *
 * Under the hood it uses P6Spy to spy on the jdbc connection and hooks into {@link PreparedStatement#executeQuery()}
 * to redirect the call to WireMock.
 *
 * If WireMock returns 404 (i.e. no match was found), an {@link AssertionError} is thrown.
 */
public class UseWireMockToMockJdbcResultSetsTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    JdbcTemplate jdbcTemplate;

    @Before
    public void before() {
        JdbcServiceVirtualizationFactory myP6MockFactory = new JdbcServiceVirtualizationFactory();
        myP6MockFactory.setTargetUrl("http://localhost:" + wireMockRule.port() + "/sqlstub");

        DataSource dataSource = myP6MockFactory.createMockDataSource();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    public void intercepts_matching_query_and_responds_with_mockresultset() {
        final String NAME_ERICH_EICHINGER = "Erich Eichinger";

        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                    // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("SELECT birthday FROM PEOPLE WHERE name = ?"))
                    // Parameters are sent with index has headername and value as headervalue
                .withHeader("1", WireMock.equalTo(NAME_ERICH_EICHINGER))
                    // return a recordset
                .willReturn(WireMock
                        .aResponse()
                        .withBody(""
                                + "<resultset>"
                                + "     <cols><col>birthday</col></cols>"
                                + "     <row><val>1980-01-01</val></row>"
                                + "</resultset>"
                        )
                )
        )
        ;

        String dateTime = jdbcTemplate
            .queryForObject(
                "SELECT birthday FROM PEOPLE WHERE name = ?"
                , String.class
                , NAME_ERICH_EICHINGER
            );

        assertThat(dateTime, equalTo("1980-01-01"));
    }

    @Test
    public void intercepts_matching_update_and_responds_with_int() {
        final String NAME_ERICH_EICHINGER = "Erich Eichinger";

        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                    // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("UPDATE PEOPLE set birthday=? WHERE name=?"))
                    // Parameters are sent with index has headername and value as headervalue
                .withHeader("1", WireMock.equalTo("1970-01-01"))
                .withHeader("2", WireMock.equalTo(NAME_ERICH_EICHINGER))
                    // return the number of rows affected
                .willReturn(WireMock
                        .aResponse()
                        .withStatus(200)
                        .withBody("2")
                )
        )
        ;

        int res = jdbcTemplate.update(
                "UPDATE PEOPLE set birthday=? WHERE name=?", "1970-01-01", NAME_ERICH_EICHINGER
            );

        assertThat(res, equalTo(2));
    }

    @Test
    public void intercepts_matching_query_and_responds_with_multi_column_mockresultset() {
        final String NAME_ERICH_EICHINGER = "Erich Eichinger";
        final String PLACE_OF_BIRTH = "Vienna";

        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("SELECT birthday, placeofbirth FROM PEOPLE WHERE name = ?"))
                // Parameters are sent with index has headername and value as headervalue
                .withHeader("1", WireMock.equalTo(NAME_ERICH_EICHINGER))
                // return a recordset
                .willReturn(WireMock
                        .aResponse()
                        .withBody(""
                                + "<resultset>"
                                + "     <cols><col>birthday</col><col>placeofbirth</col></cols>"
                                + "     <row>"
                                + "         <birthday>1980-01-01</birthday>"
                                + "         <placeofbirth>" + PLACE_OF_BIRTH + "</placeofbirth>"
                                + "     </row>"
                                + "</resultset>"
                        )
                )
        )
        ;

        String[] result = jdbcTemplate.queryForObject(
                "SELECT birthday, placeofbirth FROM PEOPLE WHERE name = ?"
                , new Object[] { NAME_ERICH_EICHINGER }
                , (rs, rowNum) -> {
                    return new String[] { rs.getString(1), rs.getString(2) };
                }
        );

        assertThat(result[0], equalTo("1980-01-01"));
        assertThat(result[1], equalTo(PLACE_OF_BIRTH));
    }

    @Test
    public void intercepts_matching_batch_update_and_responds_with_int_array() {
        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("INSERT INTO PEOPLE (name, birthdate, placeOfBirth) " +
                        "VALUES (?, ?, ?)"))
                // Parameters are sent with index has headername and value as headervalue
                .withHeader("1", WireMock.matching(".+"))
                .withHeader("2", WireMock.matching(".+"))
                .withHeader("3", WireMock.matching(".+"))
                // return a recordset
                .willReturn(WireMock
                        .aResponse()
                        .withBody(""
                                + "-2,-2"
                        )
                )
        );

        List<Person> persons = new ArrayList<Person>() {{
            add(new Person("Erich Erichinger", "1980-01-01", "Vienna"));
            add(new Person("Matthias Bernloehr", "1990-01-01", "Germany"));
            add(new Person("Steffen Wegner", "1990-01-01", "Germany"));
            add(new Person("Volker Waltner", "1980-01-01", "Germany"));
        }};

        int[][] result = jdbcTemplate.batchUpdate("INSERT INTO PEOPLE (name, birthdate, placeOfBirth) " +
                "VALUES (?, ?, ?)", persons, 2, (ps, argument) -> {
            ps.setString(1, argument.getName());
            ps.setString(2, argument.getBirthdate());
            ps.setString(3, argument.getPlaceOfBirth());
        });

        assertThat(result.length, equalTo(2));
        assertThat(result[0][0], equalTo(-2));
        assertThat(result[0][1], equalTo(-2));
        assertThat(result[1][0], equalTo(-2));
        assertThat(result[1][1], equalTo(-2));
    }


    @Test
    public void emulate_sqlexception_by_returning_400() {
        thrown.expect(BadSqlGrammarException.class);

        final String NAME = "Hugo Simon";

        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                    // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("SYNTAX ERROR"))
                    // return a recordset
                .willReturn(WireMock
                        .aResponse()
                        .withStatus(400)
                        .withHeader("reason", "unexpected token: SYNTAX")
                        .withHeader("SQLState", "42581")
                        .withHeader("vendorCode", "1234")
                )
        )
        ;

        String dateTime = jdbcTemplate
            .queryForObject(
                "SYNTAX ERROR"
                , String.class
                , NAME
            );
    }

    @Test
    public void passthrough_nonmatching_queries_throws_assertionerror() {
        thrown.expect(AssertionError.class);

        final String NAME = "Hugo Simon";

        String dateTime = jdbcTemplate
            .queryForObject(
                "SELECT birthday FROM PEOPLE WHERE name = ?"
                , String.class
                , NAME
            );
    }

    @Value
    private static class Person {
        String name;
        String birthdate;
        String placeOfBirth;
    }

}
