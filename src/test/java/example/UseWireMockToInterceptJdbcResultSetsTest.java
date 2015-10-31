package example;

import javax.sql.DataSource;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.mockrunner.mock.jdbc.JDBCMockObjectFactory;
import com.mockrunner.mock.jdbc.MockDataSource;
import lombok.SneakyThrows;
import org.hsqldb.HsqlException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactory;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class UseWireMockToInterceptJdbcResultSetsTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    JdbcTemplate jdbcTemplate;

    @SneakyThrows
    public DataSource createHSQLDataSource() {
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

        EmbeddedDatabaseFactory dbFactory = new EmbeddedDatabaseFactory();
        dbFactory.setDatabaseType(EmbeddedDatabaseType.HSQL);
        dbFactory.setDatabasePopulator(dbPopulator);

        return dbFactory.getDatabase();
    }

    @Before
    public void before() {
        MyP6MockFactory myP6MockFactory = new MyP6MockFactory();
        myP6MockFactory.setTargetUrl("http://localhost:" + wireMockRule.port() + "/sqlstub");

        DataSource dataSource = myP6MockFactory.spyOnDataSource(createHSQLDataSource());
//        DataSource dataSource = myP6MockFactory.createMockDataSource();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    public void intercepts_matching_query_and_responds_with_mockresultset() {
        final String NAME_ERICH_EICHINGER = "Erich Eichinger";

        // setup mock resultsets
        WireMock.stubFor(WireMock
                .post(WireMock.urlPathEqualTo("/sqlstub"))
                    // SQL Statement is posted in the body, use any available matchers to match
                .withRequestBody(WireMock.equalTo("SELECT birthday FROM DM.PEOPLE WHERE name = ?"))
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
                "SELECT birthday FROM DM.PEOPLE WHERE name = ?"
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
        thrown.expect(HsqlException.class);
        thrown.expectMessage("unexpected token: SYNTAX");

        final String NAME = "Hugo Simon";

        String dateTime = jdbcTemplate
            .queryForObject(
                "SYNTAX ERROR STATEMENT"
                , String.class
                , NAME
            );

        assertThat(dateTime, equalTo("2012-01-02"));
    }
}
