/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.integrations.destination.keen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joestelmach.natty.Parser;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.protocol.models.ConfiguredAirbyteCatalog;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used for timestamp inference. Keen leverages use of time-related data for it's
 * analytics, so it's important to have timestamp values for historical data if possible. If stream
 * contains cursor field, then its value is used as a timestamp, if parsing it is possible.
 */
public class KeenTimestampService {

  private static final Logger LOGGER = LoggerFactory.getLogger(KeenRecordsConsumer.class);

  private static final long SECONDS_FROM_EPOCH_THRESHOLD = 1_000_000_000L;

  private static final long MILLIS_FROM_EPOCH_THRESHOLD = 10_000_000_000L;

  // Map containing stream names paired with their cursor fields
  private Map<String, List<String>> streamCursorFields;
  private final Parser parser;
  private final boolean timestampInferenceEnabled;

  public KeenTimestampService(ConfiguredAirbyteCatalog catalog, boolean timestampInferenceEnabled) {
    this.streamCursorFields = new HashMap<>();
    this.parser = new Parser();
    this.timestampInferenceEnabled = timestampInferenceEnabled;

    if (timestampInferenceEnabled) {
      LOGGER.info("Initializing KeenTimestampService, finding cursor fields.");
      streamCursorFields = catalog.getStreams()
          .stream()
          .filter(stream -> stream.getCursorField().size() > 0)
          .map(s -> Pair.of(s.getStream().getName(), s.getCursorField()))
          .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
  }

  /**
   * Tries to inject keen.timestamp field to the given message data. If the stream contains cursor
   * field, it's value is tried to be parsed to timestamp. If this procedure fails, stream is removed
   * from timestamp-parsable stream map, so parsing is not tried for future messages in the same stream.
   * If parsing succeeds, keen.timestamp field is put as a JSON node to the message data and whole data
   * is returned. Otherwise, keen.timestamp is set to emittedAt value
   *
   * @param message AirbyteRecordMessage containing record data
   * @return Record data together with keen.timestamp field
   */
  public JsonNode injectTimestamp(AirbyteRecordMessage message) {
    String streamName = message.getStream();
    List<String> cursorField = streamCursorFields.get(streamName);
    JsonNode data = message.getData();
    if (timestampInferenceEnabled && cursorField != null) {
      try {
        String timestamp = parseTimestamp(cursorField, data);
        injectTimestamp(data, timestamp);
      } catch (Exception e) {
        // If parsing of timestamp has failed, remove stream from timestamp-parsable stream map,
        // so it won't be parsed for future messages.
        LOGGER.info("Unable to parse cursor field: {} into a keen.timestamp", cursorField);
        streamCursorFields.remove(streamName);
        injectTimestamp(data, Instant.ofEpochMilli(message.getEmittedAt()).toString());
      }
    } else {
      injectTimestamp(data, Instant.ofEpochMilli(message.getEmittedAt()).toString());
    }
    return data;
  }

  private void injectTimestamp(JsonNode data, String timestamp) {
    ObjectNode root = ((ObjectNode) data);
    root.set("keen", JsonNodeFactory.instance.objectNode().put("timestamp", timestamp));
  }

  private String parseTimestamp(List<String> cursorField, JsonNode data) {
    JsonNode timestamp = getNestedNode(data, cursorField);
    long numberTimestamp = timestamp.asLong();
    // if cursor value is below given threshold, assume that it's not epoch timestamp but ordered id
    if (numberTimestamp >= SECONDS_FROM_EPOCH_THRESHOLD) {
      return dateFromNumber(numberTimestamp);
    }
    // if timestamp is 0, then parsing it to long failed - let's try with String now
    if (numberTimestamp == 0) {
      return parser
          .parse(timestamp.asText())
          .get(0).getDates()
          .get(0)
          .toInstant()
          .toString();
    }
    throw new IllegalStateException();
  }

  private String dateFromNumber(Long timestamp) {
    // if cursor value is above given threshold, then assume that it's Unix timestamp in milliseconds
    if (timestamp > MILLIS_FROM_EPOCH_THRESHOLD) {
      return Instant.ofEpochMilli(timestamp).toString();
    }
    return Instant.ofEpochSecond(timestamp).toString();
  }

  private static JsonNode getNestedNode(JsonNode data, List<String> fieldNames) {
    return fieldNames.stream().reduce(data, JsonNode::get, (first, second) -> second);
  }

  public Map<String, List<String>> getStreamCursorFields() {
    return streamCursorFields;
  }

}
