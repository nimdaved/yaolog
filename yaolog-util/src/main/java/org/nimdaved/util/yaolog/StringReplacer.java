package org.nimdaved.util.yaolog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Log related string utility:
 * 1. Replacing new lines to avoid log interleaving
 * 2. String obfuscation to hide sensitive information
 * 3. Conversion between camel and snake format
 * 4. Metod duration message
 */
public final class StringReplacer {

  private static final String OBFUSCATION_OVERLAY = "******";
  private static final String JSON_PASSWORD_REPLACEMENT = "\"$1\":\"" + OBFUSCATION_OVERLAY + "\"";
  private static final int SHOW_FIRST_LAST_CHARS = 6;
  private static final String LOW_HIGH = "([a-z])([A-Z])";
  private static final String HIGH_LOW = "([A-Z]+)([A-Z][a-z])";
  private static final String REPLACEMENT = "$1_$2";
  private static final String NEW_LINE_MARKER = "\u2028";

  private final static Pattern REGEX_JSON_PASSWORD =
      Pattern.compile("\"(?i)(password|pwd|ssn|cvc|creditCardNumber|credit_card_number)\":\"[\\w\\p{Punct}&&[^&]]*?\"");

  /**
   * Substitutes  new all line characters with NEW_LINE_MARKERs in the String
   * @param message any string
   * @return String with substituted new line characters
   */
  public static String replaceNewLine(String message) {
    return Optional.ofNullable(message).map(m -> m.replace(System.lineSeparator(), NEW_LINE_MARKER))
        .orElse(null);
  }

  /**
   * String representation of exception that substitutes  new all line characters with NEW_LINE_MARKERs
   * in the exception message and stack trace representation
   * @param t throwable to parse
   * @return String representation of t
   */
  public static String replaceNewLine(Throwable t) {
    return (t == null) ? null
        : new StringBuilder(" | ").append(replaceNewLine(ExceptionUtils.getMessage(t)))
            .append(" | ").append(replaceNewLine(ExceptionUtils.getRootCauseMessage(t)))
            .append(NEW_LINE_MARKER).append(replaceNewLine(ExceptionUtils.getStackTrace(t)))
            .toString();
  }

  /**
   * Replaces camel elements into "snake" (e.g.: abcXyz to abc_xyz).
   * It is useful for json normalization
   * @param in string with or without camel expression
   * @return converted string
   */
  public static String camelToSnake(String in) {
    return Optional.ofNullable(in).map(camel -> camel.replaceAll(HIGH_LOW, REPLACEMENT)
        .replaceAll(LOW_HIGH, REPLACEMENT).toLowerCase()).orElse(null);
  }

  /**
   * Normalizes String keys in the map to the "snake" format.
   * It is useful for normalization of the json representation by map
   * @param in map with string keys
   * @return map with keys converted to "snake"
   * @param <T> value type
   */
  public static <T> Map<String, T> camelKeysToSnake(Map<String, T> in) {
    return Optional.ofNullable(in)
        .map(m -> m.entrySet().stream()
            .collect(Collectors.toMap(e -> camelToSnake(e.getKey()), e -> e.getValue())))
        .orElse(null);
  }

  /**
   * String representation of exception that substitutes  new all line characters with NEW_LINE_MARKERs
   * in the exception message and stack trace
   * @param message Additional exception context
   * @param t Throwable to parse
   * @return Exception String representation without new line characters
   */
  public static String throwableMessage(String message, Throwable t) {
    return replaceNewLine(message) + replaceNewLine(t);
  }

  /**
   * Constructs duration message when startTime is known.
   * @param methodName name of the method
   * @param startTime previously measured method start time
   * @return "Stopwatch" string representation
   */
  public static String methodDurationMessage(String methodName, Instant startTime) {
    return String.format("Method duration: %s: %s ", methodName,
        startTime != null ? ("" + ChronoUnit.MILLIS.between(startTime, Instant.now()) + " msec")
            : "unknown. Start time is missing");
  }

  /**
   * Partially obfuscates sensitive information with charsToShow number of characters to show
   * @param secret String with sensitive information
   * @param charsToShow number of characters in the secret's beginning and end to be shown
   * @return Obfuscated secret
   */
  public static String obfuscate(String secret, int charsToShow) {
    int overlayEnd = Math.max(charsToShow, StringUtils.length(secret) - charsToShow);
    return StringUtils.overlay(secret, OBFUSCATION_OVERLAY, charsToShow, overlayEnd);
  }

  /**
   * Partially obfuscates sensitive information with default number of characters to show
   * @param secret String with sensitive information
   * @return Obfyscated secret
   */
  public static String obfuscate(String secret) {
    return obfuscate(secret, SHOW_FIRST_LAST_CHARS);
  }

  /**
   * Obfusctes values for certain keys in json
   * @param secret  Json string with sensitive information
   * @return Json string with obfuscated secret
   */
  public static String obfuscateInJson(String secret) {
    return REGEX_JSON_PASSWORD.matcher(secret).replaceAll(JSON_PASSWORD_REPLACEMENT);
  }

  private StringReplacer() {
  }
}
