package org.eeichinger.servicevirtualisation.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Erich Eichinger
 * @since 22/07/16
 */
public class MockDataSourceThreadSafetyTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(0);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    DataSource dataSource;

    @Before
    public void before() {
        JdbcServiceVirtualizationFactory myP6MockFactory = new JdbcServiceVirtualizationFactory();
        myP6MockFactory.setTargetUrl("http://localhost:" + wireMockRule.port() + "/sqlstub");

        dataSource = myP6MockFactory.createMockDataSource();
    }

    @Test
    public void intercepts_matching_preparedstatement_and_responds_with_mockresultset() throws Throwable {
        final Connection connection = dataSource.getConnection();

        final int count = 20000;
        final ExecutorService executor = Executors.newFixedThreadPool(1000);

        Random random = new Random(System.currentTimeMillis());

        ArrayList<Callable<Throwable>> tasks = new ArrayList<>();
        for(int i=0; i<count;i++) {
            final String sql = "SELECT birthday FROM PEOPLE WHERE name = ? AND " + i + "=" + i;
            tasks.add(() -> {
                try {
                    Thread.sleep(random.nextInt(50));
                    final PreparedStatement ps = connection.prepareStatement(sql);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            });
        }

        final List<Future<Throwable>> results = executor.invokeAll(tasks);
        for(int i=0;i<count;i++) {
            final Throwable caughtException = results.get(i).get();
            if (caughtException != null) {
                throw caughtException;
            }
        }
    }
}
