package org.eeichinger.servicevirtualisation.jdbc;

import com.mockrunner.mock.jdbc.MockResultSet;
import com.p6spy.engine.common.ConnectionInformation;
import lombok.SneakyThrows;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;


public class WireMockMappingJsonRecorderTest {
    @Test
    @SneakyThrows
    public void testWireMockRecording() {
        WireMockMappingJsonRecorder recorder = new WireMockMappingJsonRecorder();
        JdbcServiceVirtualizationFactory.PreparedStatementInformation preparedStatementInformation = new JdbcServiceVirtualizationFactory.PreparedStatementInformation(new ConnectionInformation());
        preparedStatementInformation.setSql("SELECT birthday, placeofbirth FROM PEOPLE WHERE name = ?");
        preparedStatementInformation.setParameterValue(0, "Erich Eichinger");

        MockResultSet resultSet = new MockResultSet("x");
        resultSet.addColumn("birthday");
        resultSet.addColumn("placeofbirth");
        resultSet.addRow(Arrays.asList("1980-01-01", "Vienna"));

        new File("src/test/resources/newMappings/").mkdirs();
        recorder.writeOutMapping(preparedStatementInformation, resultSet);

        List<String> expectedLines = Files.readAllLines(Paths.get("src/test/resources/expectedRecording.json"));
        List<String> actualLines = Files.readAllLines(Paths.get("src/test/resources/newMappings/1.json"));

        assertThat(actualLines, equalTo(expectedLines));
    }

    @Test
    public void testJsonsAreAddedSequentially() {
        new File("src/test/resources/newMappings/").mkdirs();
        for (File file : new File("src/test/resources/newMappings/").listFiles()) {
            file.delete();
        }

        assertThat(new File("src/test/resources/newMappings/1.json").exists(), equalTo(false));
        assertThat(new File("src/test/resources/newMappings/2.json").exists(), equalTo(false));

        WireMockMappingJsonRecorder recorder = new WireMockMappingJsonRecorder();
        JdbcServiceVirtualizationFactory.PreparedStatementInformation preparedStatementInformation = new JdbcServiceVirtualizationFactory.PreparedStatementInformation(new ConnectionInformation());
        MockResultSet resultSet = new MockResultSet("x");

        recorder.writeOutMapping(preparedStatementInformation, resultSet);
        assertThat(new File("src/test/resources/newMappings/1.json").exists(), equalTo(true));
        assertThat(new File("src/test/resources/newMappings/2.json").exists(), equalTo(false));

        recorder.writeOutMapping(preparedStatementInformation, resultSet);
        assertThat(new File("src/test/resources/newMappings/1.json").exists(), equalTo(true));
        assertThat(new File("src/test/resources/newMappings/2.json").exists(), equalTo(true));

    }
}
