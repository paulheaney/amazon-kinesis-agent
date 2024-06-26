/*
 * Copyright 2014-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License. 
 * A copy of the License is located at
 * 
 *  http://aws.amazon.com/asl/
 *  
 * or in the "license" file accompanying this file. 
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.amazon.kinesis.streaming.agent.processing.processors;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.amazon.kinesis.streaming.agent.ByteBuffers;
import com.amazon.kinesis.streaming.agent.config.Configuration;
import com.amazon.kinesis.streaming.agent.processing.exceptions.DataConversionException;
import com.amazon.kinesis.streaming.agent.processing.exceptions.LogParsingException;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IDataConverter;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IJSONPrinter;
import com.amazon.kinesis.streaming.agent.processing.interfaces.ILogParser;
import com.amazon.kinesis.streaming.agent.processing.utils.ProcessingUtilsFactory;

/**
 * Parse the log entries from log file, and convert the log entries into JSON.
 * 
 * Configuration of this converter looks like:
 * {
 *     "optionName": "LOGTOJSON",
 *     "logFormat": "COMMONAPACHELOG",
 *     "matchPattern": "OPTIONAL_REGEX",
 *     "customFieldNames": [ "column1", "column2", ... ]
 * }
 * 
 * @author chaocheq
 *
 */
public class LogToJSONDataConverter implements IDataConverter {
    
    private List<String> fields;
    private ILogParser logParser;
    private IJSONPrinter jsonProducer;
    private static String STATIC_FIELDS_KEY = "staticFields";
    private final Map<String, Object> staticFields;
    
    public LogToJSONDataConverter(Configuration config) {
        jsonProducer = ProcessingUtilsFactory.getPrinter(config);
        logParser = ProcessingUtilsFactory.getLogParser(config);
        if (config.containsKey(ProcessingUtilsFactory.CUSTOM_FIELDS_KEY)) {
            fields = config.readList(ProcessingUtilsFactory.CUSTOM_FIELDS_KEY, String.class);
        }

        if(config.containsKey(STATIC_FIELDS_KEY)){
            Configuration configuration = config.readConfiguration(STATIC_FIELDS_KEY);
            staticFields = configuration.getConfigMap();
        } else {
            staticFields = new HashMap<String, Object>();
        }
    }

    @Override
    public ByteBuffer convert(ByteBuffer data) throws DataConversionException {
        String dataStr = ByteBuffers.toString(data, StandardCharsets.UTF_8);
        
        // Preserve the NEW_LINE at the end of the JSON record
        if (dataStr.endsWith(NEW_LINE)) {
            dataStr = dataStr.substring(0, (dataStr.length() - NEW_LINE.length()));
        }
        
        Map<String, Object> recordMap;
        
        try {
            recordMap = logParser.parseLogRecord(dataStr, fields);
        } catch (LogParsingException e) {
            // ignore the record if a LogParsingException is thrown
            // the record is filtered out in this case
            LoggerFactory.getLogger(getClass()).error("Getting exception while parsing record: [" + dataStr
                    + "], with pattern ["  + logParser.getPattern() + "], record will be skipped");
        	LoggerFactory.getLogger(getClass()).debug("Getting exception while parsing record: [" + dataStr
                    + "], with pattern ["  + logParser.getPattern() + "], record will be skipped", e);
            return null;
        }

        for (String key : staticFields.keySet()) {
            recordMap.put(key, staticFields.get(key));
        }

        String dataJson = jsonProducer.writeAsString(recordMap) + NEW_LINE;
        return ByteBuffer.wrap(dataJson.getBytes(StandardCharsets.UTF_8));
    }
}
