/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.string;

import static com.datastax.driver.core.DataType.cdouble;
import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.date;
import static com.datastax.driver.core.DataType.list;
import static com.datastax.driver.core.DataType.map;
import static com.datastax.driver.core.DataType.varchar;
import static com.datastax.driver.core.DriverCoreEngineTestHooks.newField;
import static com.datastax.driver.core.DriverCoreEngineTestHooks.newUserType;
import static com.datastax.driver.core.TypeCodec.userType;
import static com.datastax.dsbulk.engine.tests.EngineAssertions.assertThat;
import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_EVEN;
import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;
import com.datastax.driver.extras.codecs.jdk8.LocalDateCodec;
import com.datastax.dsbulk.engine.internal.codecs.ConvertingCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToDoubleCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToIntegerCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToListCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToLocalDateCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToMapCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToUDTCodec;
import com.datastax.dsbulk.engine.internal.codecs.util.CqlTemporalFormat;
import com.datastax.dsbulk.engine.internal.codecs.util.OverflowStrategy;
import com.datastax.dsbulk.engine.internal.settings.CodecSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.netty.util.concurrent.FastThreadLocal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StringToUDTCodecTest {

  private final ObjectMapper objectMapper = CodecSettings.getObjectMapper();

  private final CodecRegistry codecRegistry = new CodecRegistry().register(LocalDateCodec.instance);

  private final FastThreadLocal<NumberFormat> numberFormat =
      CodecSettings.getNumberFormatThreadLocal("#,###.##", US, HALF_EVEN, true);

  private final List<String> nullStrings = newArrayList("NULL");

  // UDT 1

  private final UserType udt1 =
      newUserType(
          codecRegistry, newField("f1a", cint()), newField("f1b", map(varchar(), cdouble())));

  private final UDTValue udt1Empty = udt1.newValue().setToNull("f1a").setToNull("f1b");
  private final UDTValue udt1Value =
      udt1.newValue().setInt("f1a", 42).setMap("f1b", newMap("foo", 1234.56d, "bar", 0.12d));

  private final ConvertingCodec f1aCodec =
      new JsonNodeToIntegerCodec(
          numberFormat,
          OverflowStrategy.REJECT,
          RoundingMode.HALF_EVEN,
          CqlTemporalFormat.DEFAULT_INSTANCE,
          UTC,
          MILLISECONDS,
          EPOCH.atZone(UTC),
          ImmutableMap.of("true", true, "false", false),
          newArrayList(ONE, ZERO),
          nullStrings);
  private final ConvertingCodec f1bCodec =
      new JsonNodeToMapCodec<>(
          TypeCodec.map(TypeCodec.varchar(), TypeCodec.cdouble()),
          new StringToStringCodec(TypeCodec.varchar(), nullStrings),
          new JsonNodeToDoubleCodec(
              numberFormat,
              OverflowStrategy.REJECT,
              RoundingMode.HALF_EVEN,
              CqlTemporalFormat.DEFAULT_INSTANCE,
              UTC,
              MILLISECONDS,
              EPOCH.atZone(UTC),
              ImmutableMap.of("true", true, "false", false),
              newArrayList(ONE, ZERO),
              nullStrings),
          objectMapper,
          nullStrings);

  @SuppressWarnings("unchecked")
  private final StringToUDTCodec udtCodec1 =
      new StringToUDTCodec(
          new JsonNodeToUDTCodec(
              userType(udt1),
              ImmutableMap.of("f1a", f1aCodec, "f1b", f1bCodec),
              objectMapper,
              nullStrings),
          objectMapper,
          nullStrings);

  // UDT 2

  private final UserType udt2 =
      newUserType(codecRegistry, newField("f2a", udt1), newField("f2b", list(date())));

  private final UDTValue udt2Empty = udt2.newValue().setToNull("f2a").setToNull("f2b");
  private final UDTValue udt2Value =
      udt2.newValue()
          .setUDTValue("f2a", udt1Value)
          .set("f2b", newList(LocalDate.of(2017, 9, 22)), TypeCodec.list(LocalDateCodec.instance));

  @SuppressWarnings("unchecked")
  private final ConvertingCodec f2aCodec =
      new JsonNodeToUDTCodec(
          userType(udt1),
          ImmutableMap.of("f1a", f1aCodec, "f1b", f1bCodec),
          objectMapper,
          nullStrings);

  private final ConvertingCodec f2bCodec =
      new JsonNodeToListCodec<>(
          TypeCodec.list(LocalDateCodec.instance),
          new JsonNodeToLocalDateCodec(CqlTemporalFormat.DEFAULT_INSTANCE, nullStrings),
          objectMapper,
          nullStrings);

  @SuppressWarnings("unchecked")
  private final StringToUDTCodec udtCodec2 =
      new StringToUDTCodec(
          new JsonNodeToUDTCodec(
              userType(udt2),
              ImmutableMap.of("f2a", f2aCodec, "f2b", f2bCodec),
              objectMapper,
              nullStrings),
          objectMapper,
          nullStrings);

  @Test
  void should_convert_from_valid_external() {
    assertThat(udtCodec1)
        .convertsFromExternal("{\"f1a\":42,\"f1b\":{\"foo\":1234.56,\"bar\":0.12}}")
        .toInternal(udt1Value)
        .convertsFromExternal("{'f1a':42,'f1b':{'foo':1234.56,'bar':0.12}}")
        .toInternal(udt1Value)
        .convertsFromExternal(
            "{ \"f1b\" :  { \"foo\" : \"1,234.56\" , \"bar\" : \"0000.12000\" } , \"f1a\" : \"42.00\" }")
        .toInternal(udt1Value)
        .convertsFromExternal("{ \"f1b\" :  null , \"f1a\" :  null }")
        .toInternal(udt1Empty)
        .convertsFromExternal(null)
        .toInternal(null)
        .convertsFromExternal("NULL")
        .toInternal(null)
        .convertsFromExternal("{}")
        .toInternal(null)
        .convertsFromExternal("")
        .toInternal(null);
    assertThat(udtCodec2)
        .convertsFromExternal(
            "{\"f2a\":{\"f1a\":42,\"f1b\":{\"foo\":1234.56,\"bar\":0.12}},\"f2b\":[\"2017-09-22\"]}")
        .toInternal(udt2Value)
        .convertsFromExternal(
            "{'f2a':{'f1a':42,'f1b':{'foo':1234.56,'bar':0.12}},'f2b':['2017-09-22']}")
        .toInternal(udt2Value)
        .convertsFromExternal(
            "{ \"f2b\" :  [ \"2017-09-22\" ] , \"f2a\" : { \"f1b\" :  { \"foo\" : \"1,234.56\" , \"bar\" : \"0000.12000\" } , \"f1a\" : \"42.00\" } }")
        .toInternal(udt2Value)
        .convertsFromExternal("{ \"f2b\" :  null , \"f2a\" :  null }")
        .toInternal(udt2Empty)
        .convertsFromExternal(null)
        .toInternal(null)
        .convertsFromExternal("NULL")
        .toInternal(null)
        .convertsFromExternal("{}")
        .toInternal(null)
        .convertsFromExternal("")
        .toInternal(null);
  }

  @Test
  void should_convert_from_valid_internal() {
    assertThat(udtCodec1)
        .convertsFromInternal(
            udt1.newValue().setInt("f1a", 42).setMap("f1b", newMap("foo", 1234.56d, "bar", 0.12d)))
        .toExternal("{\"f1a\":42,\"f1b\":{\"foo\":1234.56,\"bar\":0.12}}")
        .convertsFromInternal(udt1.newValue())
        .toExternal("{\"f1a\":null,\"f1b\":{}}")
        .convertsFromInternal(null)
        .toExternal("NULL");
  }

  @Test
  void should_not_convert_from_invalid_external() {
    assertThat(udtCodec1)
        .cannotConvertFromExternal("{\"f1a\":42}")
        .cannotConvertFromExternal("[42]")
        .cannotConvertFromExternal("{\"not a valid input\":\"foo\"}");
  }

  @SuppressWarnings("SameParameterValue")
  private static Map<String, Double> newMap(String k1, Double v1, String k2, Double v2) {
    Map<String, Double> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  private static List<LocalDate> newList(LocalDate... elements) {
    return Arrays.asList(elements);
  }
}
