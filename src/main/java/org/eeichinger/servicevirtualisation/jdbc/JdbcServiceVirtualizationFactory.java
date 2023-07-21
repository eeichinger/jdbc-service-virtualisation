package org.eeichinger.servicevirtualisation.jdbc;

import com.mockrunner.jdbc.CallableStatementResultSetHandler;
import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.jdbc.StatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockConnection;
import com.mockrunner.mock.jdbc.MockDataSource;
import com.mockrunner.mock.jdbc.MockResultSet;
import com.mockrunner.mock.jdbc.MockStatement;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.proxy.Delegate;
import com.p6spy.engine.proxy.GenericInvocationHandler;
import com.p6spy.engine.proxy.MethodNameMatcher;
import com.p6spy.engine.proxy.ProxyFactory;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.sql.DataSource;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * This is implemented as a {@link P6Factory}, the plan is to integrate it as a P6Module.
 *
 * @author Erich Eichinger
 * @since 30/10/2015
 */
public class JdbcServiceVirtualizationFactory implements P6Factory {

    @Getter @Setter
    private String targetUrl;

    @Setter
    private WireMockMappingJsonRecorder recorder;

    public DataSource spyOnDataSource(DataSource ds) {
        return interceptDataSource(ds);
    }

    public DataSource createMockDataSource() {
        MockDataSource dataSource = new MockDataSource() {{
            setupConnection(new StubbingMockConnection());
        }};
        return interceptDataSource(dataSource);
    }

    private static class StubbingMockConnection extends MockConnection {
        public StubbingMockConnection() {
            this(new SynchronizedStatementResultSetHandler()
                , new PreparedStatementResultSetHandler() {
                    @Override
                    public SQLException getSQLException(String sql) {
                        throw new AssertionError("unmatched sql statement: '" + sql + "'");
                    }
                }
                , new CallableStatementResultSetHandler() {
                    @Override
                    public SQLException getSQLException(String sql) {
                        throw new AssertionError("unmatched sql statement: '" + sql + "'");
                    }
                }
            );
        }

        public StubbingMockConnection(StatementResultSetHandler statementHandler, PreparedStatementResultSetHandler preparedStatementHandler, CallableStatementResultSetHandler callableStatementHandler) {
            super(synchronizeMembers(statementHandler), synchronizeMembers(preparedStatementHandler), synchronizeMembers(callableStatementHandler));
        }

        @SneakyThrows
        private static <T> T synchronizeMembers(T o) {
            doWithFields(o.getClass(), f->syncField(o, f));
            for(Field f : o.getClass().getDeclaredFields()) {
                syncField(o, f);
            }
            return o;
        }

        private static void doWithFields(Class<?> clazz, Consumer<Field> fc) {
            // Keep backing up the inheritance hierarchy.
            Class<?> targetClass = clazz;
            do {
                Field[] fields = targetClass.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    fc.accept(field);
                }
                targetClass = targetClass.getSuperclass();
            }
            while (targetClass != null && targetClass != Object.class);
        }

        @SneakyThrows
        private static <T> void syncField(T o, Field f) {
            Class<?> fieldType = f.getType();
            Object value = f.get(o);
            if (List.class.isAssignableFrom(fieldType) && value != null) {
                f.set(o, Collections.synchronizedList((List<?>) value));
            } else if (Map.class.isAssignableFrom(fieldType) && value != null) {
                f.set(o, Collections.synchronizedMap((Map<?,?>)value));
            }
        }

        private static class SynchronizedStatementResultSetHandler extends StatementResultSetHandler {
            @Override
            public SQLException getSQLException(String sql) {
                throw new AssertionError("unmatched sql statement: '" + sql + "'");
            }

            @Override
            public synchronized void addStatement(MockStatement statement) {
                super.addStatement(statement);
            }
        }
    }

    @Override
    public Connection getConnection(Connection conn) {
        return interceptConnection(conn);
    }

    @Override
    public P6LoadableOptions getOptions(P6OptionsRepository optionsRepository) {
        // TODO
        return new P6LogOptions(optionsRepository);
    }

    protected DataSource interceptDataSource(DataSource ds) {
        GenericInvocationHandler<DataSource> invocationHandler = createDataSourceInvocationHandler(ds);
        return ProxyFactory.createProxy(ds, invocationHandler);
    }

    protected Connection interceptConnection(Connection conn) {
        GenericInvocationHandler<Connection> invocationHandler = createConnectionInvocationHandler(conn);
        return ProxyFactory.createProxy(conn, invocationHandler);
    }

    /**
     * This is where the magic happens - decide whether to intercept or pass through
     *
     * @param preparedStatementInformation
     * @param underlying
     * @param method
     * @param args
     * @return the resultset (executeQuery) or int (executeUpdate)
     */
    @SneakyThrows
    protected Object interceptPreparedStatementExecution(PreparedStatementInformation preparedStatementInformation, Object underlying, Method method, Object[] args) {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpPost httpPost = prepareHttpCall(preparedStatementInformation);

        try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                return getResultFromHttpResponse(response, method.getReturnType());
            }
            if (response.getStatusLine().getStatusCode() == 400) {
                throw getExceptionFromResponse(response);
            }
        }

        return callUnderlyingMethod(underlying, method, args, preparedStatementInformation);
    }

    protected HttpPost prepareHttpCall(PreparedStatementInformation preparedStatementInformation) {
        final String sql = preparedStatementInformation.getSql();
        HttpPost httpPost = new HttpPost(targetUrl);
        for (Map.Entry<Integer, Object> e : preparedStatementInformation.getParameterValues().entrySet()) {
            httpPost.setHeader(e.getKey().toString(), Objects.toString(e.getValue()));
        }
        httpPost.setEntity(new StringEntity(sql, "utf-8"));
        return httpPost;
    }

    @SneakyThrows
    protected Object getResultFromHttpResponse(CloseableHttpResponse response, Class<?> returnType) {
        String responseContent = EntityUtils.toString(response.getEntity(), "utf-8");
        if (int[].class.equals(returnType)) {
            return parseBatchUpdateRowsAffected(responseContent);
        }
        if (int.class.equals(returnType)) {
            return Integer.parseInt(responseContent);
        }
        return parseResultSetFromResponseContent(responseContent);
    }

    protected MockResultSet parseResultSetFromResponseContent(String responseContent) {
        return new MockResultSetHelper().parseResultSetFromSybaseXmlString("x", responseContent);
    }

    protected Throwable getExceptionFromResponse(CloseableHttpResponse response) {
        final Header reasonHeader = response.getFirstHeader("reason");
        if (reasonHeader == null) return new AssertionError("missing 'reason' response header");
        final String sqlState = response.getFirstHeader("sqlstate") != null ? response.getFirstHeader("sqlstate").getValue() : null;
        final int vendorCode = response.getFirstHeader("vendorcode") != null ? Integer.parseInt(response.getFirstHeader("vendorcode").getValue()) : 0;
        return new SQLException(reasonHeader.getValue(), sqlState, vendorCode);
    }

    @SneakyThrows
    public ResultSet cacheResultSet(ResultSet resultSet) {
        CachedRowSet cachedRowSet = RowSetProvider.newFactory().createCachedRowSet();
        cachedRowSet.populate(resultSet);
        return cachedRowSet;
    }

    @SneakyThrows
    protected Object callUnderlyingMethod(Object underlying, Method method, Object[] args, PreparedStatementInformation preparedStatementInformation) {
        Object actualResult = method.invoke(underlying, args);

        if (recorder != null && actualResult instanceof ResultSet) {
            ResultSet cachedResultSet = cacheResultSet((ResultSet) actualResult);
            recorder.writeOutMapping(preparedStatementInformation, cachedResultSet);
            cachedResultSet.beforeFirst();
        }

        return actualResult;

    }


    @RequiredArgsConstructor
    static class PreparedStatementInformation {
        @Getter private final ConnectionInformation connectionInformation;
        @Getter @Setter private String sql;
        @Getter private final Map<Integer, Object> parameterValues = new HashMap<>();

        public void setParameterValue(int position, Object value) {
            parameterValues.put(position, value);
        }
    }

    protected Delegate createDataSourceGetConnectionDelegate() {
        return (final Object proxy, final Object underlying, final Method method, final Object[] args) -> {
            Connection conn = (Connection) method.invoke(underlying, args);
            return interceptConnection(conn);
        };
    }

    protected Delegate createConnectionPrepareStatementDelegate(final ConnectionInformation connectionInformation) {
        return (final Object proxy, final Object underlying, final Method method, final Object[] args) -> {
            synchronized (this) {
                PreparedStatement statement = (PreparedStatement) method.invoke(underlying, args);
                String query = (String) args[0];
                GenericInvocationHandler<PreparedStatement> invocationHandler = createPreparedStatementInvocationHandler(connectionInformation, statement, query);
                return ProxyFactory.createProxy(statement, invocationHandler);
            }
        };
    }

    protected Delegate createPreparedStatementExecuteDelegate(final PreparedStatementInformation preparedStatementInformation) {
        return (final Object proxy, final Object underlying, final Method method, final Object[] args) -> {
            synchronized (preparedStatementInformation.getConnectionInformation()) {
                return interceptPreparedStatementExecution(preparedStatementInformation, underlying, method, args);
            }
        };
    }

    protected class P6MockDataSourceInvocationHandler extends GenericInvocationHandler<DataSource> {

        public P6MockDataSourceInvocationHandler(DataSource underlying) {
            super(underlying);

            Delegate getConnectionDelegate = createDataSourceGetConnectionDelegate();

            // add delegates to return proxies for other methods
            addDelegate(
                new MethodNameMatcher("getConnection"),
                getConnectionDelegate
            );
        }
    }

    protected class P6MockConnectionInvocationHandler extends GenericInvocationHandler<Connection> {

        public P6MockConnectionInvocationHandler(Connection underlying) {
            super(underlying);
            ConnectionInformation connectionInformation = new ConnectionInformation();

            Delegate prepareStatementDelegate = createConnectionPrepareStatementDelegate(connectionInformation);

            // add delegates to return proxies for other methods
            addDelegate(
                new MethodNameMatcher("prepareStatement"),
                prepareStatementDelegate
            );
        }
    }

    protected class P6MockPreparedStatementInvocationHandler extends GenericInvocationHandler<PreparedStatement> {

        class P6MockPreparedStatementSetParameterValueDelegate implements Delegate {
            protected final PreparedStatementInformation preparedStatementInformation;

            public P6MockPreparedStatementSetParameterValueDelegate(PreparedStatementInformation preparedStatementInformation) {
                this.preparedStatementInformation = preparedStatementInformation;
            }

            @Override
            public Object invoke(final Object proxy, final Object underlying, final Method method, final Object[] args) throws Throwable {
                // ignore calls to any methods defined on the Statement interface!
                if (!Statement.class.equals(method.getDeclaringClass())) {
                    int position = (Integer) args[0];
                    Object value = null;
                    if (!method.getName().equals("setNull") && args.length > 1) {
                        value = args[1];
                    }
                    preparedStatementInformation.setParameterValue(position, value);
                }
                return method.invoke(underlying, args);
            }


        }

        public P6MockPreparedStatementInvocationHandler(PreparedStatement underlying,
                                                        ConnectionInformation connectionInformation,
                                                        String query) {

            super(underlying);
            PreparedStatementInformation preparedStatementInformation = new PreparedStatementInformation(connectionInformation);
            preparedStatementInformation.setSql(query);

            Delegate executeDelegate = createPreparedStatementExecuteDelegate(preparedStatementInformation);
            Delegate setParameterValueDelegate = new P6MockPreparedStatementSetParameterValueDelegate(preparedStatementInformation);

            addDelegate(
                new MethodNameMatcher("executeBatch"),
                executeDelegate
            );
            addDelegate(
                new MethodNameMatcher("execute"),
                executeDelegate
            );
            addDelegate(
                new MethodNameMatcher("executeQuery"),
                executeDelegate
            );
            addDelegate(
                new MethodNameMatcher("executeUpdate"),
                executeDelegate
            );
            addDelegate(
                new MethodNameMatcher("set*"),
                setParameterValueDelegate
            );
        }
    }

    protected P6MockDataSourceInvocationHandler createDataSourceInvocationHandler(DataSource dataSource) {
        return new P6MockDataSourceInvocationHandler(dataSource);
    }

    protected P6MockConnectionInvocationHandler createConnectionInvocationHandler(Connection conn) {
        return new P6MockConnectionInvocationHandler(conn);
    }

    protected P6MockPreparedStatementInvocationHandler createPreparedStatementInvocationHandler(ConnectionInformation connectionInformation, PreparedStatement statement, String query) {
        return new P6MockPreparedStatementInvocationHandler(statement, connectionInformation, query);
    }

    /**
     * Returns an integer array with all number of affected rows for one batch.
     * List must be comma-seperated, e.g. "-2,-2,-2".
     *
     * @return array with corresponding number of updated rows for each batch
     */
    private static int[] parseBatchUpdateRowsAffected(String responseContent) {
        return Stream.of(responseContent.split(",")).mapToInt(Integer::parseInt).toArray();
    }

}

