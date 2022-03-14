package org.nimdaved.util.yaolog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public final class StringReplacer {

  private static final String OBFUSCATION_OVERLAY = "******";
  private static final String JSON_PASSWORD_REPLACEMENT = "\"$1\":\"" + OBFUSCATION_OVERLAY + "\"";
  private static final int SHOW_FIRST_LAST_CHARS = 6;
  private static final String LOW_HIGH = "([a-z])([A-Z])";
  private static final String HIGH_LOW = "([A-Z]+)([A-Z][a-z])";
  private static final String REPLACEMENT = "$1_$2";
  private static final String NEW_LINE_MARKER = "\u2028";

  private final static Pattern REGEX_JSON_PASSWORD =
      Pattern.compile("\"(?i)(password|pwd)\":\"[\\w\\p{Punct}&&[^&]]*?\"");

  public static String replaceNewLine(String message) {
    return Optional.ofNullable(message).map(m -> m.replace(System.lineSeparator(), NEW_LINE_MARKER))
        .orElse(null);
  }

  public static String replaceNewLine(Throwable t) {
    return (t == null) ? null
        : new StringBuilder(" | ").append(replaceNewLine(ExceptionUtils.getMessage(t)))
            .append(" | ").append(replaceNewLine(ExceptionUtils.getRootCauseMessage(t)))
            .append(NEW_LINE_MARKER).append(replaceNewLine(ExceptionUtils.getStackTrace(t)))
            .toString();
  }

  public static String camelToSnake(String in) {
    return Optional.ofNullable(in).map(camel -> camel.replaceAll(HIGH_LOW, REPLACEMENT)
        .replaceAll(LOW_HIGH, REPLACEMENT).toLowerCase()).orElse(null);
  }

  public static <T> Map<String, T> camelKeysToSnake(Map<String, T> in) {
    return Optional.ofNullable(in)
        .map(m -> m.entrySet().stream()
            .collect(Collectors.toMap(e -> camelToSnake(e.getKey()), e -> e.getValue())))
        .orElse(null);
  }

  public static String throwableMessage(String message, Throwable t) {
    return replaceNewLine(message) + replaceNewLine(t);
  }

  public static String methodDurationMessage(String methodName, Instant startTime) {
    return String.format("Method duration: %s: %s ", methodName,
        startTime != null ? ("" + ChronoUnit.MILLIS.between(startTime, Instant.now()) + " msec")
            : "unknown. Start time is missing");
  }

  public static String obfuscate(String secret, int charsToShow) {
    int overlayEnd = Math.max(charsToShow, StringUtils.length(secret) - charsToShow);
    return StringUtils.overlay(secret, OBFUSCATION_OVERLAY, charsToShow, overlayEnd);
  }

  public static String obfuscate(String secret) {
    return obfuscate(secret, SHOW_FIRST_LAST_CHARS);
  }

  public static String obfuscateInJson(String secret) {
    return REGEX_JSON_PASSWORD.matcher(secret).replaceAll(JSON_PASSWORD_REPLACEMENT);
  }

}
