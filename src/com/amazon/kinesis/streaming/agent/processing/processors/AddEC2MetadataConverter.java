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
import java.util.*;
import java.text.SimpleDateFormat;

import com.amazon.kinesis.streaming.agent.ByteBuffers;
import com.amazon.kinesis.streaming.agent.config.Configuration;
import com.amazon.kinesis.streaming.agent.processing.exceptions.DataConversionException;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IDataConverter;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IJSONPrinter;
import com.amazon.kinesis.streaming.agent.processing.interfaces.ILogParser;
import com.amazon.kinesis.streaming.agent.processing.utils.ProcessingUtilsFactory;
import com.amazonaws.util.EC2MetadataUtils;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse the log entries from log file, and convert the log entries into JSON.
 *
 * Configuration of this converter looks like:
 * {
 *     "optionName": "ADDEC2METADATA",
 *     "logFormat": "RFC3339SYSLOG"
 * }
 *
 * @author buholzer
 *
 */
public class AddEC2MetadataConverter implements IDataConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AddEC2MetadataConverter.class);
  private static final String PRIVATE_IP = "privateIp";
  private static final String AVAILABILITY_ZONE = "availabilityZone";
  private static final String AVAILABILITY_ZONE_INITIAL = "availabilityZoneInitial";
  private static final String INSTANCE_ID = "instanceId";
  private static final String INSTANCE_NAME = "instanceName";
  private static final String INSTANCE_TYPE = "instanceType";
  private static final String ACCOUNT_ID = "accountId";
  private static final String AMI_ID = "amiId";
  private static final String REGION = "region";
  private static final String METADATA_TIMESTAMP = "metadataTimestamp";
  private static final String TAGS = "tags";
  private ILogParser logParser;
  private IJSONPrinter jsonProducer;
  private Map<String, Object> metadata;

  private List<String> metadataFields;
  private long metadataTimestamp;

  private String metadataPrefix = "";
  private long metadataTTL = 1000 * 60 * 60; // Update metadata every hour

  public AddEC2MetadataConverter(Configuration config) {
    jsonProducer = ProcessingUtilsFactory.getPrinter(config);
    logParser = ProcessingUtilsFactory.getLogParser(config);

    if (config.containsKey("metadataTTL")) {
      try {
        metadataTTL = config.readInteger("metadataTTL") * 1000;
        LOGGER.info("Setting metadata TTL to {} millis", metadataTTL);
      } catch(Exception ex) {
        LOGGER.warn("Error converting metadataTTL, ignoring");
      }
    }

    if (config.containsKey("metadataFields")) {
      metadataFields = config.readList("metadataFields", String.class);
      LOGGER.info("Using custom metadata fields {}", metadataFields);
    } else {
      LOGGER.info("Using default metadata fields");
      metadataFields = Arrays.asList(PRIVATE_IP,
              AVAILABILITY_ZONE,
              AVAILABILITY_ZONE_INITIAL,
              INSTANCE_ID,
              INSTANCE_TYPE,
              ACCOUNT_ID,
              AMI_ID,
              REGION,
              METADATA_TIMESTAMP,
              TAGS);
    }

    if (config.containsKey("metadataPrefix")) {
      metadataPrefix = config.readString("metadataPrefix");
    }

    refreshEC2Metadata();
  }

  @Override
  public ByteBuffer convert(ByteBuffer data) throws DataConversionException {

    if ((metadataTimestamp + metadataTTL) < System.currentTimeMillis()) refreshEC2Metadata();
    
    if (metadata == null || metadata.isEmpty()) {
      LOGGER.warn("Unable to append metadata, no metadata found");
      return data;
    }

    String dataStr = ByteBuffers.toString(data, StandardCharsets.UTF_8);

    ObjectMapper mapper = new ObjectMapper();
    TypeReference<LinkedHashMap<String,Object>> typeRef = 
      new TypeReference<LinkedHashMap<String,Object>>() {};

    LinkedHashMap<String,Object> dataObj = null;
    try {
      dataObj = mapper.readValue(dataStr, typeRef);
    } catch (Exception ex) {
      throw new DataConversionException("Error converting json source data to map", ex);
    }

    // Appending EC2 metadata
    dataObj.putAll(metadata);

    String dataJson = jsonProducer.writeAsString(dataObj) + NEW_LINE;
    LOGGER.debug("Appended metadata, output: {}", dataJson);
    return ByteBuffer.wrap(dataJson.getBytes(StandardCharsets.UTF_8));
  }

  String getZoneLetter(String zone) {
    if (StringUtils.isNotEmpty(zone)) {
      return zone.substring(zone.length() - 1);
    }
    return null;
  }

  private void refreshEC2Metadata() {
    LOGGER.info("Refreshing EC2 metadata");

    metadataTimestamp = System.currentTimeMillis();
    
    try {
      EC2MetadataUtils.InstanceInfo info = EC2MetadataUtils.getInstanceInfo();

      metadata = new LinkedHashMap<String, Object>();
      if (metadataFields.contains(PRIVATE_IP)) {
        metadata.put(metadataPrefix + PRIVATE_IP, info.getPrivateIp());
      }

      if (metadataFields.contains(AVAILABILITY_ZONE)) {
        metadata.put(metadataPrefix + AVAILABILITY_ZONE, info.getAvailabilityZone());
      }

      if (metadataFields.contains(AVAILABILITY_ZONE_INITIAL)) {
        metadata.put(metadataPrefix + AVAILABILITY_ZONE, getZoneLetter(info.getAvailabilityZone()));
      }

      if (metadataFields.contains(INSTANCE_ID)) {
        metadata.put(metadataPrefix + INSTANCE_ID, info.getInstanceId());
      }

      if (metadataFields.contains(INSTANCE_TYPE)) {
        metadata.put(metadataPrefix + INSTANCE_TYPE, info.getInstanceType());
      }

      if (metadataFields.contains(ACCOUNT_ID)) {
        metadata.put(metadataPrefix + ACCOUNT_ID, info.getAccountId());
      }

      if (metadataFields.contains(AMI_ID)) {
        metadata.put(metadataPrefix + AMI_ID, info.getImageId());
      }

      if (metadataFields.contains(REGION)) {
        metadata.put(metadataPrefix + REGION, info.getRegion());
      }

      if (metadataFields.contains(METADATA_TIMESTAMP)) {
        metadata.put(metadataPrefix + METADATA_TIMESTAMP,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                        .format(new Date(metadataTimestamp)));
      }

      final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
      DescribeTagsResult result = ec2.describeTags(
              new DescribeTagsRequest().withFilters(
                      new Filter().withName("resource-id").withValues(info.getInstanceId())));
      List<TagDescription> tags = result.getTags();

      if (metadataFields.contains(TAGS)) {
        Map<String, Object> metadataTags = new LinkedHashMap<String, Object>();
        for (TagDescription tag : tags) {
          metadataTags.put(tag.getKey().toLowerCase(), tag.getValue());
        }

        metadata.put(metadataPrefix + TAGS, metadataTags);
      }

      if (metadataFields.contains(INSTANCE_NAME)) {
        for (TagDescription tag : tags) {
          if (tag.getKey().equalsIgnoreCase("name")) {
            metadata.put(metadataPrefix + INSTANCE_NAME, tag.getValue());
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.warn("Error while updating EC2 metadata - " + ex.getMessage() + ", ignoring");
    }
  }
}
