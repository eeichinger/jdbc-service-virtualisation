package example;

import java.sql.PreparedStatement;

import javax.sql.DataSource;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.oaky.poc.servicevirtualisation.JdbcServiceVirtualizationFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

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
}
