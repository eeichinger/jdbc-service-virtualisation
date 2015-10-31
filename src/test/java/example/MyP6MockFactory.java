package example;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import javax.sql.DataSource;

import com.mockrunner.base.NestedApplicationException;
import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.JDBCMockObjectFactory;
import com.mockrunner.mock.jdbc.MockConnection;
import com.mockrunner.mock.jdbc.MockParameterMap;
import com.mockrunner.mock.jdbc.MockResultSet;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.proxy.Delegate;
import com.p6spy.engine.proxy.GenericInvocationHandler;
import com.p6spy.engine.proxy.MethodNameMatcher;
import com.p6spy.engine.proxy.ProxyFactory;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * @author Erich Eichinger
 * @since 30/10/2015
 */
public class MyP6MockFactory implements P6Factory {

    private String targetUrl;

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public DataSource spyOnDataSource(DataSource ds) {
        return interceptDataSource(ds);
    }

    public DataSource createMockDataSource() {
        JDBCMockObjectFactory jdbcMockObjectFactory = new JDBCMockObjectFactory() {
            @Override
            public void registerMockDriver() {
                // we don't want to auto-hijack DriverManager
            }

            @Override
            public void restoreDrivers() {
                // we don't want to auto-hijack DriverManager
            }
        };
        final MockConnection mockConnection = jdbcMockObjectFactory.getMockConnection();
        final PreparedStatementResultSetHandler rsHandler = mockConnection.getPreparedStatementResultSetHandler();
        rsHandler.setUseRegularExpressions(true);
        rsHandler.prepareResultSet(".*", new MockResultSet("unmatched") {
            @Override
            public MockResultSet evaluate(String sql, MockParameterMap parameters) {
                throw new AssertionError("unmatched sql statement: '" + sql + "'");
            }
        });
        jdbcMockObjectFactory.getMockDataSource().setupConnection(mockConnection);
        return interceptDataSource(jdbcMockObjectFactory.getMockDataSource());
    }

    @Override
    public Connection getConnection(Connection conn) throws SQLException {
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

    static class PreparedStatementInformation {
        ConnectionInformation connectionInformation;
        String sql;
        Map<Integer, Object> parameterValues = new HashMap<Integer, Object>();

        public PreparedStatementInformation(ConnectionInformation connectionInformation) {
            this.connectionInformation = connectionInformation;
        }

        public ConnectionInformation getConnectionInformation() {
            return connectionInformation;
        }

        public String getSql() {
            return sql;
        }

        public Map<Integer, Object> getParameterValues() {
            return parameterValues;
        }

        public void setStatementQuery(String sql) {
            this.sql = sql;
        }

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

    protected P6MockDataSourceInvocationHandler createDataSourceInvocationHandler(DataSource dataSource) {
        return new P6MockDataSourceInvocationHandler(dataSource);
    }

    protected P6MockConnectionInvocationHandler createConnectionInvocationHandler(Connection conn) {
        return new P6MockConnectionInvocationHandler(conn);
    }

    protected P6MockConnectionPrepareStatementDelegate createConnectionPrepareStatementDelegate(ConnectionInformation connectionInformation) {
        return new P6MockConnectionPrepareStatementDelegate(connectionInformation);
    }

    protected P6MockPreparedStatementInvocationHandler createPreparedStatementInvocationHandler(ConnectionInformation connectionInformation, PreparedStatement statement, String query) {
        return new P6MockPreparedStatementInvocationHandler(statement, connectionInformation, query);
    }

    protected P6MockPreparedStatementExecuteDelegate createPreparedStatementExecuteDelegate(PreparedStatementInformation preparedStatementInformation) {
        return new P6MockPreparedStatementExecuteDelegate(preparedStatementInformation);
    }

    public class P6MockDataSourceInvocationHandler extends GenericInvocationHandler<DataSource> {

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

    public class P6MockConnectionInvocationHandler extends GenericInvocationHandler<Connection> {

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

    public class P6MockConnectionPrepareStatementDelegate implements Delegate {

        private final ConnectionInformation connectionInformation;

        public ConnectionInformation getConnectionInformation() {
            return connectionInformation;
        }

        public P6MockConnectionPrepareStatementDelegate(final ConnectionInformation connectionInformation) {
            this.connectionInformation = connectionInformation;
        }

        @Override
        public Object invoke(final Object proxy, final Object underlying, final Method method, final Object[] args) throws Throwable {
            PreparedStatement statement = (PreparedStatement) method.invoke(underlying, args);
            String query = (String) args[0];
            GenericInvocationHandler<PreparedStatement> invocationHandler = createPreparedStatementInvocationHandler(getConnectionInformation(), statement, query);
            return ProxyFactory.createProxy(statement, invocationHandler);
        }

    }

    public class P6MockPreparedStatementInvocationHandler extends GenericInvocationHandler<PreparedStatement> {

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
            preparedStatementInformation.setStatementQuery(query);

            P6MockPreparedStatementExecuteDelegate executeDelegate = createPreparedStatementExecuteDelegate(preparedStatementInformation);
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

    public class P6MockPreparedStatementExecuteDelegate implements Delegate {
        private final PreparedStatementInformation preparedStatementInformation;

        public P6MockPreparedStatementExecuteDelegate(final PreparedStatementInformation preparedStatementInformation) {
            this.preparedStatementInformation = preparedStatementInformation;
        }

        @Override
        public Object invoke(final Object proxy, final Object underlying, final Method method, final Object[] args) throws Throwable {

            CloseableHttpClient httpclient = HttpClients.createDefault();

            final String sql = preparedStatementInformation.getSql();
            HttpPost httpPost = new HttpPost(targetUrl);
            for (Map.Entry<Integer, Object> e : preparedStatementInformation.getParameterValues().entrySet()) {
                httpPost.setHeader(e.getKey().toString(), Objects.toString(e.getValue()));
            }
            httpPost.setEntity(new StringEntity(sql, "utf-8"));

            try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
                String responseContent = EntityUtils.toString(response.getEntity());
                if (response.getStatusLine().getStatusCode() == 200) {
                    return createSybaseResultSet(true, "x", responseContent);
                }
            }

            return method.invoke(underlying, args);
        }
    }

    /**
     * Return a MockResultSet with proper column names and
     * rows based on the XML <code>Document</code>.
     *
     * @return MockResultSet Results read from XML
     * <code>Document</code>.
     */
    public MockResultSet createSybaseResultSet(boolean trim, String id, String xml) {
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

