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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.amazon.kinesis.streaming.agent.ByteBuffers;
import com.amazon.kinesis.streaming.agent.config.Configuration;
import com.amazon.kinesis.streaming.agent.processing.exceptions.DataConversionException;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IDataConverter;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IJSONPrinter;
import com.amazon.kinesis.streaming.agent.processing.utils.ProcessingUtilsFactory;

/**
 * Convert a CSV record into JSON record.
 * 
 * customFieldNames is required. 
 * Optional delimiter other than comma can be configured.
 * Optional jsonFormat can be used for pretty printed json.
 * 
 * Configuration looks like:
 * {
 *     "optionName": "CSVTOJSON",
 *     "customFieldNames": [ "field1", "field2", ... ],
 *     "delimiter": "\\t"
 * }
 * 
 * @author chaocheq
 *
 */
public class CSVToJSONDataConverter implements IDataConverter {
    
    private static String FIELDS_KEY = "customFieldNames";
    private static String DELIMITER_KEY = "delimiter";
    private final List<String> fieldNames;
    private final String delimiter;
    private final IJSONPrinter jsonProducer;
    private static String STATIC_FIELDS_KEY = "staticFields";
    private final Map<String, Object> staticFields;
    
    public CSVToJSONDataConverter(Configuration config) {
        fieldNames = config.readList(FIELDS_KEY, String.class);
        delimiter = config.readString(DELIMITER_KEY, ",");
        jsonProducer = ProcessingUtilsFactory.getPrinter(config);

        if(config.containsKey(STATIC_FIELDS_KEY)){
            Configuration configuration = config.readConfiguration(STATIC_FIELDS_KEY);
            staticFields = configuration.getConfigMap();
        } else {
            staticFields = new HashMap<String, Object>();
        }
    }

    @Override
    public ByteBuffer convert(ByteBuffer data) throws DataConversionException {
        final Map<String, Object> recordMap = new LinkedHashMap<String, Object>();
        String dataStr = ByteBuffers.toString(data, StandardCharsets.UTF_8);
        
        // Preserve the NEW_LINE at the end of the JSON record
        if (dataStr.endsWith(NEW_LINE)) {
            dataStr = dataStr.substring(0, (dataStr.length() - NEW_LINE.length()));
        }
        
        String[] columns = dataStr.split(delimiter);
        
        for (int i = 0; i < fieldNames.size(); i++) {
            try {
                if (columns[i] != null) {
                    recordMap.put(fieldNames.get(i), columns[i].trim());
                } else {
                    recordMap.put(fieldNames.get(i), columns[i]);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            	LoggerFactory.getLogger(getClass()).debug("Null field in CSV detected");
                recordMap.put(fieldNames.get(i), null);
            } catch (Exception e) {
                throw new DataConversionException("Unable to create the column map", e);
            }
        }

        for (String key : staticFields.keySet()) {
            recordMap.put(key, staticFields.get(key));
        }

        String dataJson = jsonProducer.writeAsString(recordMap) + NEW_LINE;
        
        return ByteBuffer.wrap(dataJson.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + 
               "{ delimiter: [" + delimiter + "], " +
               "fields: " + fieldNames.toString() + "}";
    }
}
