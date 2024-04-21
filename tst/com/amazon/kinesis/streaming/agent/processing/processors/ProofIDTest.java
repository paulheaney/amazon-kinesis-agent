package com.amazon.kinesis.streaming.agent.processing.processors;

import com.amazon.kinesis.streaming.agent.config.Configuration;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IDataConverter;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProofIDTest extends DataConverterTest {

    @Test
    public void testMatls() throws Exception {
        final Configuration config = new Configuration(new HashMap<String, Object>() {{
            put("optionName", "LOGTOJSON");
            put("logFormat", "SYSLOG");
            put("matchPattern", "timestamp=\"([^\\\"]*)\" client=\"([0-9A-Fa-f:\\.]*)\" request=\"([^\\\"]*)\" request_length=(\\d+) bytes_sent=(\\d+) body_bytes_sent=(\\d+) user_agent=\"([^\\\"]*)\" upstream_addr=\"([0-9A-Fa-f:\\.-]*)\" upstream_status=([0-9\\-]*) request_time=(\\d+\\.\\d+) upstream_response_time=(\\d+\\.\\d+|-) upstream_connect_time=(\\d+\\.\\d+|-) upstream_header_time=(\\d+\\.\\d+|-) connection=(\\d+)");
            put("customFieldNames", Arrays.asList("timestamp", "client", "request", "requestlength", "bytessent", "bodybytessent", "useragent", "upstreamaddr", "upstreamstatus", "requesttime", "upstreamresponsetime", "upstreamconnecttime", "upstreamheadertime", "connection"));
        }});
        final IDataConverter converter = new LogToJSONDataConverter(config);

        final String dataStr = "timestamp=\"17/Apr/2022:18:03:19 +0000\" client=\"10.10.10.36\" request=\"GET /health.html HTTP/1.1\" request_length=139 bytes_sent=225 body_bytes_sent=3 user_agent=\"ELB-HealthChecker/2.0\" upstream_addr=\"-\" upstream_status=- request_time=0.000 upstream_response_time=- upstream_connect_time=- upstream_header_time=- connection=160";
        final String expectedStr = "{\"column1\":\"123.45.67.89\",\"column2\":null,\"column3\":null,\"column4\":\"27/Oct/2000:09:27:09 -0400\",\"column5\":\"GET /java/javaResources.html HTTP/1.0\",\"column6\":\"200\"}\n";
        ByteBuffer res = converter.convert(ByteBuffer.wrap(dataStr.getBytes()));
        String s = new String(res.array());
        System.out.println(s);

        verifyDataConversion(converter, dataStr.getBytes(), expectedStr.getBytes());
    }

    @Test
    public void test() {
        // String str = "timestamp=\"18/Apr/2022:04:18:52 +0000\" client=\"10.10.10.36\" request=\"GET /health.html HTTP/1.1\" request_length=139 bytes_sent=225 body_bytes_sent=3 user_agent=\"ELB-HealthChecker/2.0\" upstream_addr=\"-\" upstream_status=- request_time=0.000 upstream_response_time=- upstream_connect_time=- upstream_header_time=- connection=11304 ";
        String str = "timestamp=\"18/Apr/2022:17:25:05 +0000\" client=\"10.10.10.36\" request=\"GET /health.html HTTP/1.1\" request_length=139 bytes_sent=225 body_bytes_sent=3 user_agent=\"ELB-HealthChecker/2.0\" upstream_addr=\"-\" upstream_status=0.0 request_time=0.000 upstream_response_time=0.0 upstream_connect_time=0.0 upstream_header_time=0.0 connection=87 ";
         String pattern = "timestamp=\"([^\"]*)\" client=\"([0-9A-Fa-f:\\.]*)\" request=\"([^\\\"]*)\" request_length=(\\d+) bytes_sent=(\\d+) body_bytes_sent=(\\d+) user_agent=\"([^\\\"]*)\" upstream_addr=\"([0-9A-Fa-f:\\.-]*)\" upstream_status=([0-9\\.\\-]*) request_time=(\\d+\\.\\d+) upstream_response_time=(\\d+\\.\\d+|-) upstream_connect_time=(\\d+\\.\\d+|-) upstream_header_time=(\\d+\\.\\d+|-) connection=(\\d+)\\s*";
//        String pattern = "timestamp=\"([^\"]*)\" client=\"([0-9A-Fa-f:\\.]*)\" .*";

        Pattern p = Pattern.compile(pattern);

        Matcher m = p.matcher(str);

        if (m.matches()) {
            System.out.println("matched");
        } else {
            System.out.println("Str: '" + str + "'");
            System.out.println("Pattern: '" + pattern + "'");
            System.out.println("not");
        }
    }

}
