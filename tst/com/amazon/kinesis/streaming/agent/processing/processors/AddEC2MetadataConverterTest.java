package com.amazon.kinesis.streaming.agent.processing.processors;

import com.amazon.kinesis.streaming.agent.config.Configuration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;

public class AddEC2MetadataConverterTest {

    @Test
    public void testGetZoneLetter() {
        final Configuration config = new Configuration(new HashMap<String, Object>() {{
            put("optionName", "ADDEC2METADATA");
            put("logFormat", "RFC3339SYSLOG");
            put("metadataFields", Arrays.asList("availabilityZoneInitial"));
        }});
        AddEC2MetadataConverter addEC2MetadataConverter = new AddEC2MetadataConverter(config);

        Assert.assertEquals("b", addEC2MetadataConverter.getZoneLetter("us-east-1b"));
    }

}
