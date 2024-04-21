package com.amazon.kinesis.streaming.agent.processing.processors;

import com.amazon.kinesis.streaming.agent.ByteBuffers;
import com.amazon.kinesis.streaming.agent.processing.exceptions.DataConversionException;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IDataConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MaTLSConverter implements IDataConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaTLSConverter.class);

    @Override
    public ByteBuffer convert(ByteBuffer data) throws DataConversionException {
        /*
        {"timestamp":"17/Apr/2022:18:03:19 +0000","client":"10.10.10.36",
        "request":"GET /health.html HTTP/1.1","requestlength":"139","bytessent":"225",
        "bodybytessent":"3","useragent":"ELB-HealthChecker/2.0",
        "upstreamaddr":"-","upstreamstatus":"-","requesttime":"0.000","upstreamresponsetime":"-","upstreamconnecttime":"-",
        "upstreamheadertime":"-","connection":"160"}

         */

        String dataStr = ByteBuffers.toString(data, StandardCharsets.UTF_8);
        dataStr = dataStr.replace("\"upstreamaddr\":\"-\"", "\"upstreamaddr\":\"0\"");
        dataStr = dataStr.replace("\"upstreamstatus\":\"-\"", "\"upstreamstatus\":\"0\"");
        dataStr = dataStr.replace("\"upstreamresponsetime\":\"-\"", "\"upstreamresponsetime\":\"0\"");
        dataStr = dataStr.replace("\"upstreamconnecttime\":\"-\"", "\"upstreamconnecttime\":\"0\"");
        dataStr = dataStr.replace("\"upstreamheadertime\":\"-\"", "\"upstreamheadertime\":\"0\"");
        LOGGER.debug("Result: " + dataStr);
        return ByteBuffer.wrap(dataStr.getBytes(StandardCharsets.UTF_8));
    }
}
