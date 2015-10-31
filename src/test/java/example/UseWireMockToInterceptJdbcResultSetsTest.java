package example;

import java.sql.PreparedStatement;

import javax.sql.DataSource;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.eeichinger.servicevirtualisation.jdbc.JdbcServiceVirtualizationFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactoryBean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Demonstrates you to use the technique to just spy on a real database and intercept/mock only selected jdbc queries.
 *
 * Under the hood it uses P6Spy to spy on the jdbc connection and hooks into {@link PreparedStatement#executeQuery()} to
 * redirect the call to WireMock.
 *
 * If WireMock returns 404 (i.e. no match was found), the query is passed through to the real datasource.
 */
public class UseWireMockToInterceptJdbcResultSetsTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    EmbeddedDatabaseFactoryBean databaseFactory;
    JdbcTemplate jdbcTemplate;

    @SneakyThrows
    public EmbeddedDatabaseFactoryBean createHSQLDatabaseFactory() {
        final ResourceDatabasePopulator dbPopulator = new ResourceDatabasePopulator();
        dbPopulator.addScript(new ByteArrayResource(("" +
            "CREATE TABLE PEOPLE (" +
            "   name VARCHAR(200) NOT NULL" +
            ",  birthday VARCHAR(200) NOT NULL" +
            ");\n"
        ).getBytes("utf-8")));
        dbPopulator.addScript(new ByteArrayResource(("" +
            "INSERT INTO PEOPLE(name, birthday) VALUES('Hugo Simon', '2012-01-02');\n"
        ).getBytes("utf-8")));

        EmbeddedDatabaseFactoryBean dbFactory = new EmbeddedDatabaseFactoryBean();
        dbFactory.setDatabaseType(EmbeddedDatabaseType.HSQL);
        dbFactory.setDatabasePopulator(dbPopulator);
        return dbFactory;
    }

    @Before
    public void before() {
        // wiremock is listening on port WireMockRule#port(), point our Jdbc-Spy to it
        JdbcServiceVirtualizationFactory myP6MockFactory = new JdbcServiceVirtualizationFactory();
        myP6MockFactory.setTargetUrl("http://localhost:" + wireMockRule.port() + "/sqlstub");

        // wrap the real datasource so we can spy/intercept it
        databaseFactory = createHSQLDatabaseFactory();
        DataSource dataSource = myP6MockFactory.spyOnDataSource(databaseFactory.getDatabase());

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @After
    public void after() {
        databaseFactory.destroy();
    }

    @Test
    public void intercepts_matching_update_and_responds_with_int() {
        final String NAME_ERICH_EICHINGER = "Erich Eichinger";

        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                    // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("UPDATE PEOPLE set birthday=? WHERE name = ?"))
                    // return the number of rows affected
                .willReturn(WireMock
                        .aResponse()
                        .withStatus(200)
                        .withBody("2")
                )
        )
        ;

        int res = jdbcTemplate.update(
            "UPDATE PEOPLE set birthday=? WHERE name = ?", "1970-01-01", NAME_ERICH_EICHINGER
        );

        assertThat(res, equalTo(2));
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
    public void passthrough_nonmatching_queries() {
        final String NAME = "Hugo Simon";

        String dateTime = jdbcTemplate
            .queryForObject(
                "SELECT birthday FROM PEOPLE WHERE name = ?"
                , String.class
                , NAME
            );

        assertThat(dateTime, equalTo("2012-01-02"));
    }

    @Test
    public void passthrough_nonmatching_queries_with_error() {
        thrown.expect(BadSqlGrammarException.class);

        final String NAME = "Hugo Simon";

        jdbcTemplate
            .queryForObject(
                "SYNTAX ERROR STATEMENT"
                , String.class
                , NAME
            );
    }
}
