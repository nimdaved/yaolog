package org.nimdaved.util.yaolog;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Opinionated conventional and AOP logging utility for reducing code clutter during logging method invokation with parameters;
 * method exit with and without return value; and exceptions thrown within method
 *
 */
// These annotations may be removed, if LogUtil to be instantiated in Spring @Configuration files
@Component
@Aspect
public class LogUtil {

  private static final String NOT_LOGGED = "{Not logged by design.}";
  private static final String CLASS_POSTFIX_CLIENT = "Client";
  private static final String CLASS_POSTFIX_CONTROLLER = "Controller";
  private static final String SPACE = " ";
  private static final String DOT = ".";
  private static final String HIPHEN = "-";
  private static final String COLON = ":";

  private static final String METHOD_ENTRY = "Method entry";
  private static final String METHOD_ENTRY_ = METHOD_ENTRY + COLON + SPACE;
  private static final String METHOD_EXIT = "Method exit";
  private static final String METHOD_EXIT_ = METHOD_EXIT + COLON + SPACE;
  private static final String METHOD_EXIT_WITH_EXCEPTION = "Could not ";
  private static final String METHOD_DURATION = " Method duration" + COLON + SPACE;
  private static final String PARAMS = "parameters";
  private static final String RETURN_VALUE = "return value";
  private static final int DEFAULT_STACK_LEVEL = 3;
  private static final Map<Class<?>, Logger> LOGGERS = new ConcurrentHashMap<>(256);
  private static final String EXECUTION = "execution(";
  private static final String AND_NOT = ") and !";
  // Currently you need to tweak this for every major app suit, e.g. ROOT_LOG_PACKAGE = "*
  // com.acme..*.*(..)": , subject to MBIFR
  private static final String ROOT_LOG_PACKAGE = "* " +  Shadow.ROOT_PACKAGE + "..*.*(..)";

  private static Level appLogLevel = Level.DEBUG;
  private final String LOG_POINTCUT_EXPRESSION = EXECUTION + ROOT_LOG_PACKAGE + AND_NOT + EXECUTION
      + "* ..LogUtil.*(..)" + AND_NOT + EXECUTION + "* ..toString(..)" + AND_NOT + EXECUTION
      + "* ..equals(..)" + AND_NOT + EXECUTION + "* ..hashCode(..)" + AND_NOT + EXECUTION
      + "* ..compare(..)" + AND_NOT + EXECUTION + "* ..compareTo(..)" + AND_NOT + "bean(*Comparator"
      + AND_NOT + "bean(*Configuration" + AND_NOT + "bean(*PortTypeImpl" + ")";
  private @Value("${logging.level:DEBUG}") String logLevel = "DEBUG";
  // We may want to log certain exceptions at INFO level instead of default ERROR; useful for
  // XxNotFoundExceptions
  private @Value("${yaolog.exception.log.info:}") Set<String> exceptionLogInfo =
      Collections.emptySet();
  private Set<Class<?>> exceptionLogInfoClasses = Collections.emptySet();
  // We may want to log certain exceptions at WARN level instead of default ERROR; useful for
  // XxNotFoundExceptions
  private @Value("${yaolog.exception.log.warn:}") Set<String> exceptionLogWarn =
      Collections.emptySet();
  private Set<Class<?>> exceptionLogWarnClasses = Collections.emptySet();
  // We may want to suppress verbose stack trace for certain exceptions
  private @Value("${yaolog.exception.log.stacktrace.hide:}") Set<String> exceptionLogStacktraceHide =
      Collections.emptySet();
  private Set<Class<?>> exceptionLogStacktraceHideClasses = Collections.emptySet();
  // Switches logging of method durations
  private @Value("${yaolog.method.duration.log: true}") boolean logMethodDuration = true;
  // Reduces log verbosity by logging only first X number of collection
  private @Value("${yaolog.collection.log.limit: 10}") int COLLECTION_LOG_LIMIT = 10;
  // Enables auto logging at INFO level for all input classes (endpoints) having class name
  // XyController
  private @Value("${yaolog.method.info.controller: true}") boolean infoController = true;
  // Enables auto logging at INFO level for all output classes (clients) having class name XyClient
  private @Value("${yaolog.method.info.client: true}") boolean infoClient;
  private @Value("${spring.profiles.active:UNSET}") String cloudEnv;

  /**
   * Gets logger from the memory cache or LogFactory. It is usefull if Logger is not defined in the clazz
   * @param clazz
   * @return
   */
  private static Logger getLogger(Class<?> clazz) {
    return LOGGERS.computeIfAbsent(clazz, LoggerFactory::getLogger);
  }

  /**
   * Gets own class logger
   * @return
   */
  private static Logger getLogger() {
    return getLogger(LogUtil.class);
  }

  /**
   * Gets logger from the memory cache or LogFactory. It is usefull if Logger is not defined in the object.
   * Usage in any object: LogUtil.getLogger(this)
   * @param object that reqire logging
   * @return logger
   */
  private static Logger getLogger(Object object) {
    return object instanceof Logger ? (Logger) object : getLogger(object.getClass());
  }

  /**
   * Sets application log level
   * @param appLogLevel application log level
   */
  public static void setAppLogLevel(Level appLogLevel) {
    LogUtil.appLogLevel = appLogLevel;
  }

  /**
   * Gets application log level
   * @param appLogLevel application log level
   */
  public static void setAppLogLevel(String appLogLevel) {
    if (StringUtils.isNotBlank(appLogLevel)) {
      setAppLogLevel(Level.valueOf(appLogLevel));
    }
  }

  /**
   * Checks cumulative debug enablement
   * @param logger logger
   * @return true if debug enabled
   */
  public static boolean isDebugEnabled(Logger logger) {
    return logger.isDebugEnabled() && isAppLogEnabled(Level.DEBUG);
  }

  /**
   * Checks cumulative info level enablement
   * @param logger logger
   * @return true if info enabled
   */
  public static boolean isInfoEnabled(Logger logger) {
    return logger.isInfoEnabled() && isAppLogEnabled(Level.INFO);
  }

  /**
   * Checks cumulative warn level enablement
   * @param logger logger
   * @return true if warn enabled
   */
  public static boolean isWarnEnabled(Logger logger) {
    return logger.isWarnEnabled() && isAppLogEnabled(Level.WARN);
  }

  /**
   * Checks cumulative error level enablement
   * @param logger logger
   * @return true if error enabled
   */
  public static boolean isErrorEnabled(Logger logger) {
    return logger.isErrorEnabled() && isAppLogEnabled(Level.ERROR);
  }

  /**
   * Checks if application log enablement is more restrictive than logger enablement
   * @param request logging level
   * @return true if application log enablement is more restrictive than logger enablement
   */
  public static boolean isAppLogEnabled(Level request) {
    return appLogLevel.toInt() <= request.toInt();
  }

  /**
   * Emits info or debug log message, based on parameters
   * @param logger
   * @param info
   * @param debug
   * @param message
   */
  private static void infoOrDebug(Logger logger, boolean info, boolean debug, String message) {
    if (info) {
      logger.info(message);
    } else if (debug) {
      logger.debug(message);
    }
  }

  /**
   * Constructs duration message when startTime is known.
   * @param methodName name of the method
   * @param startTime previously measured method start time
   * @return "Stopwatch message" for the method logging
   */
  public static String methodDurationMessage(String methodName, Instant startTime) {
    StringBuilder sb = new StringBuilder(methodName).append(COLON).append(METHOD_DURATION);

    if (startTime != null) {
      sb.append(ChronoUnit.MILLIS.between(startTime, Instant.now())).append(" msec.");
    } else {
      sb.append(" unknown. Start time is missing");
    }
    return sb.toString();
  }

  /**
   * Null-safe logger retrival
   * @param any object that requires logging
   * @return logger
   * @param <T> parameter type
   */
  public static <T> Logger log(T any) {
    return (any == null ? getLogger() : ((any instanceof Logger) ? (Logger) any : getLogger(any)));
  }

  /**
   * Generic gedug message logger
   * @param any object that requires logging
   * @param msg log message
   * @param args message arguments
   * @param <T> parameter type
   */
  public static <T> void debug(T any, String msg, Object... args) {
    Logger logger = log(any);
    if (isDebugEnabled(log(any))) {
      if (args == null) {
        logger.debug(msg);
      } else if (args.length == 1) {
        logger.debug(msg, args[0]);
      } else if (args.length == 2) {
        logger.debug(msg, args[0], args[1]);
      } else {
        logger.debug(msg, args);
      }
    }
  }

  /**
   * Generic info message logger
   * @param any object that requires logging
   * @param msg log message
   * @param args message arguments
   * @param <T> parameter type
   */
  public static <T> void info(T any, String msg, Object... args) {
    Logger logger = log(any);
    if (isInfoEnabled(log(any))) {
      if (args == null) {
        logger.info(msg);
      } else if (args.length == 1) {
        logger.info(msg, args[0]);
      } else if (args.length == 2) {
        logger.info(msg, args[0], args[1]);
      } else {
        logger.info(msg, args);
      }
    }
  }

  /**
   * Generic warn message logger
   * @param any object that requires logging
   * @param msg log message
   * @param args message arguments
   * @param <T> parameter type
   */
  public static <T> void warn(T any, String msg, Object... args) {
    Logger logger = log(any);
    if (isWarnEnabled(log(any))) {
      if (args == null) {
        logger.warn(msg);
      } else if (args.length == 1) {
        logger.warn(msg, args[0]);
      } else if (args.length == 2) {
        logger.warn(msg, args[0], args[1]);
      } else {
        logger.warn(msg, args);
      }
    }
  }

  /**
   * Generic error message logger
   * @param any object that requires logging
   * @param msg log message
   * @param args message arguments
   * @param <T> parameter type
   */
  public static <T> void error(T any, String msg, Object... args) {
    Logger logger = log(any);
    if (isErrorEnabled(log(any))) {
      if (args == null) {
        logger.error(msg);
      } else if (args.length == 1) {
        logger.error(msg, args[0]);
      } else if (args.length == 2) {
        logger.error(msg, args[0], args[1]);
      } else {
        logger.error(msg, args);
      }
    }
  }

  /**
   * Logs method entry when method name is supplied
   * @param any object that requires logging
   * @param methodName that requires logging
   * @param parameters method parameters
   * @param <T> parameter type
   */
  public static <T> void logMethodEntry(T any, String methodName, Object... parameters) {
    if (isDebugEnabled(log(any))) {
      log(any).debug(
          getLogName(log(any)) + METHOD_ENTRY + methodWithParameters(methodName, parameters));
    }
  }

  /**
   * Logs method entry when method name is not supplied
   * @param any object that requires logging
   * @param parameters method that requires logging
   * @param <T> parameter type
   */
  public static <T> void logMethodEntry(T any, Object... parameters) {
    if (isDebugEnabled(log(any)))
      logMethodEntry(any, inferCallerName(DEFAULT_STACK_LEVEL), parameters);
  }

  /**
   * Logs method exit when method name is supplied
   * @param any object that requires logging
   * @param methodName method that requires logging
   * @param retVal method's return value
   * @param <T> parameter type
   */
  public static <T> void logMethodExit(T any, String methodName, Object... retVal) {
    if (isDebugEnabled(log(any))) {
      log(any)
          .debug(getLogName(log(any)) + METHOD_EXIT + methodWithReturnValue(methodName, retVal));
    }
  }

  /**
   * Logs method exit when method name is supplied
   * @param any object that requires logging
   * @param retVal method's return value
   * @param <T> parameter type
   */
  public static <T> void logMethodExit(T any, Object... retVal) {
    if (isDebugEnabled(log(any))) {
      logMethodExit(any, inferCallerName(DEFAULT_STACK_LEVEL), retVal);
    }
  }

  /**
   * Logs error on exception within method
   * @param any method's object
   * @param e method's exception
   * @param methodName of the method where exception is thrown
   * @param parameters method's parameters
   * @return logged message
   * @param <T> parameter type
   */
  public static <T> String errorMethodException(T any, Throwable e, String methodName,
      Object... parameters) {
    String msg = methodWithException(methodName, e, parameters);

    log(any).error(StringReplacer.throwableMessage(getLogName(log(any)), e));

    return msg;
  }

  /**
   * Logs error on exception within method
   * @param any method's object
   * @param e method's exception
   * @param parameters method's parameters
   * @return logged message
   * @param <T> parameter type
   */
  public static <T> String errorMethodException(T any, Throwable e, Object... parameters) {

    return errorMethodException(any, e, inferCallerName(DEFAULT_STACK_LEVEL), parameters);
  }

  /**
   * Logs exception, wraps it with subclass of Runtime exception, then rethrows new exception
   * @param any  object that requires logging
   * @param t original exception
   * @param wrapper subclass of Runtime exception
   * @param parameters method's parameters
   * @param <T> parameter type
   * @param <U> wrapper exception type
   */
  public static <T, U extends RuntimeException> void errorWrapThrow(T any, Throwable t,
      Class<U> wrapper, Object... parameters) {
    String msg = errorMethodException(any, t, inferCallerName(DEFAULT_STACK_LEVEL), parameters);
    throwWrapped(any, t, wrapper, msg);
  }

  private static <T> void throwWrapped(T any, Throwable t,
      Class<? extends RuntimeException> wrapper, String msg) {
    Object[] methodParams = new Object[] {any, t, wrapper, msg};

    try {
      Constructor<? extends RuntimeException> c =
          wrapper.getConstructor(String.class, Throwable.class);
      throw c.newInstance(msg, t);
    } catch (SecurityException | IllegalAccessException | InvocationTargetException e) {
      errorMethodException(LogUtil.class, e, methodParams);
    } catch (NoSuchMethodException e) {
      errorMethodException(LogUtil.class, e, methodParams);
    } catch (IllegalArgumentException e) {
      errorMethodException(LogUtil.class, e, methodParams);
    } catch (InstantiationException e) {
      errorMethodException(LogUtil.class, e, methodParams);
    }
  }

  private static String getLogName(Logger log) {
    return log.getName() + SPACE;
  }

  /**
   * Logs exception within method with debug log level
   * @param any method's object
   * @param e method's exception
   * @param methodName of the method where exception is thrown
   * @param parameters method's parameters
   * @return logged message
   * @param <T> parameter type
   */
  public static <T> String logMethodException(T any, Throwable e, String methodName,
      Object... parameters) {
    String msg = methodWithException(methodName, e, parameters);
    if (isDebugEnabled(log(any))) {
      log(any).debug(StringReplacer.throwableMessage(getLogName(log(any)), e));
    }

    return msg;
  }

  /**
   * Logs exception within method with debug log level
   * @param any method's object
   * @param e method's exception
   * @param parameters method's parameters
   * @return logged message
   * @param <T> parameter type
   */
  public static <T> String logMethodException(T any, Throwable e, Object... parameters) {
    return logMethodException(any, e, inferCallerName(DEFAULT_STACK_LEVEL), parameters);
  }

  /**
   * Logs exception at debug level, wraps it with subclass of Runtime exception, then rethrows new exception
   * @param any  object that requires logging
   * @param t original exception
   * @param wrapper subclass of Runtime exception
   * @param parameters method's parameters
   * @param <T> parameter type
   */
  public static <T> void logWrapThrow(T any, Throwable t, Class<? extends RuntimeException> wrapper,
      Object... parameters) {
    String msg = logMethodException(any, t, inferCallerName(DEFAULT_STACK_LEVEL), parameters);
    throwWrapped(any, t, wrapper, msg);
  }

  /**
   * Builds method entry log message that includes method name and parameters
   * @param methodName name of the method
   * @param parameters method parameters
   * @return message that includes method name and parameters
   */
  public static String methodWithParameters(String methodName, Object... parameters) {
    return COLON + SPACE + buildMethodName(methodName, PARAMS, HIPHEN, parameters);
  }

  /**
   * Builds method exit log message that includes method name and return value
   * @param methodName name of the method
   * @param retVal method return value. Object... data type allows to pass array
   * @return message that includes method name and return value
   */
  public static String methodWithReturnValue(String methodName, Object... retVal) {
    return COLON + SPACE + buildMethodName(methodName, RETURN_VALUE, HIPHEN, retVal);
  }

  /**
   * Builds "method exit with exception" log message that includes method name, parameters, and exception
   * @param methodName name of the method
   * @param e thrown exception
   * @param parameters method parameters
   * @return "method exit with exception" log message that includes method name, parameters, and exception
   */
  public static String methodWithException(String methodName, Throwable e, Object... parameters) {
    return METHOD_EXIT_WITH_EXCEPTION + buildMethodName(methodName, PARAMS, HIPHEN, parameters)
        + (e == null ? "" : DOT + SPACE + e.getMessage());
  }

  /**
   * Converts method parameters into String
   * @param parameters method parameters
   * @return method parameters as string
   */
  public static String getMethodParamString(Object[] parameters) {
    return new StringBuffer(METHOD_ENTRY).append(HIPHEN).append(PARAMS).append(SPACE)
        .append(Arrays.deepToString(parameters)).toString();
  }

  /**
   * Converts method return value into String
   * @param retVal method return value. Could be null or absent
   * @return method return value as String
   */
  public static String getMethodReturnString(Object... retVal) {
    return new StringBuffer(METHOD_EXIT).append(HIPHEN).append(PARAMS).append(SPACE)
        .append(Arrays.deepToString(retVal)).toString();
  }

  private static String buildMethodName(String methodName, String groupName, String separator,
      Object... parameters) {
    StringBuilder sb = new StringBuilder(methodName);

    if (getCount(parameters) != 0) {
      sb.append(DOT).append(SPACE).append(groupName).append(SPACE).append(separator).append(SPACE)
          .append(
              Arrays.deepToString(Stream.of(parameters).map(p -> lessVerbose(p, 10)).toArray()));
    }

    return StringReplacer.replaceNewLine(sb.toString());
  }

  /**
   * Infers method name by analyzing stack frame at stackLevel depth
   * @param stackLevel expected stack frame that contains method name
   * @return method name
   */
  public static String inferCallerName(int stackLevel) {
    String callerName = "";
    try {
      throw new IllegalArgumentException("inferMethodName");
    } catch (IllegalArgumentException ex) {
      if (stackLevel <= 0 || getCount(ex.getStackTrace()) < stackLevel) {
        getLogger().debug("Could not infer caller name. Invalid stack level: " + stackLevel, ex);
      } else {
        callerName = ex.getStackTrace()[stackLevel - 1].getMethodName();
      }
    }

    return callerName;
  }

  /**
   * Calculates null safe array length
   * @param values Array. Could be null
   * @return null safe array length
   * @param <T> parameter type
   */
  public static <T> int getCount(T[] values) {
    return values == null ? 0 : values.length;
  }

  /**
   * Checks if array contains any items
   * @param values Array. Could be null
   * @return true for non-empty arrays, otherwise false
   * @param <T> parameter type
   */
  public static <T> boolean hasCount(T[] values) {
    return getCount(values) != 0;
  }

  /**
   * Instantiated by AOP
   */
  public LogUtil() {
    super();
  }


  /**
   * Pointcut marker for AOP logging
   */
  @Pointcut(LOG_POINTCUT_EXPRESSION)
  private void loggingPointcut() {
    // AOP point cut marker method
  }

  /**
   * Intializes AOP logging configuration
   */
  @PostConstruct
  public void initAspects() {
    // you need this if logback is not included in classpath
    setAppLogLevel(logLevel);
    initExceptionExclusions();
    getLogger().debug(
        "CLOUD_ENVIRONMENT {}; Application log level {}; LOG_POINTCUT_EXPRESSION: {};"
            + "\n\r logMethodDuration {}; exceptionLogInfoClasses {}; exceptionLogWarnClasses {}",
        cloudEnv, appLogLevel, LOG_POINTCUT_EXPRESSION, logMethodDuration, exceptionLogInfoClasses,
        exceptionLogWarnClasses);
  }

  private void initExceptionExclusions() {
    exceptionLogInfoClasses = initExclusions(exceptionLogInfo);
    exceptionLogWarnClasses = initExclusions(exceptionLogWarn);
    exceptionLogStacktraceHideClasses = initExclusions(exceptionLogStacktraceHide);

    getLogger().debug(
        "exceptionLogInfoClasses {}; exceptionLogWarnClasses {}; exceptionLogStacktraceHideClasses {}",
        exceptionLogInfoClasses, exceptionLogWarnClasses, exceptionLogStacktraceHideClasses);
  }

  private Set<Class<?>> initExclusions(Set<String> classNames) {
    return classNames.stream().filter(s -> s != null && s.trim().length() > 0).map(this::toClass)
        .filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private Class<?> toClass(String s) {
    try {
      return Class.forName(s);
    } catch (LinkageError | ClassNotFoundException e) {
      errorMethodException(LogUtil.class, e, s);
      return null;
    }
  }

  private boolean isExclusion(Class<?> c, Set<Class<?>> exclusions) {
    return exclusions.stream().anyMatch(e -> e.isAssignableFrom(c));
  }



  private boolean infoAnnotated(JoinPoint joinPoint) {
    boolean annotated = false;
    Object target = joinPoint.getTarget();
    // Shed off Spring proxies; magic constant '5' gives sanity control against infinity
    for (int i = 0; i < 5 && target != null && AopUtils.isJdkDynamicProxy(target); i++)
      try {
        Object sourceTarget = (Object) ((Advised) target).getTargetSource().getTarget();
        if (sourceTarget == null) {
          break;
        }
        target = sourceTarget;
      } catch (Exception e) {
        errorMethodException(LogUtil.class, e, joinPoint);
      }
    Class<?> targetClass = target == null ? null : target.getClass();
    // is whole class annotated?
    if (targetClass != null) {
      annotated = targetClass.isAnnotationPresent(LogInfo.class);
    }
    if (!annotated) {
      final MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
      Method method = methodSignature.getMethod();
      // is method annotated
      annotated = method.isAnnotationPresent(LogInfo.class);
      if (!annotated && (method.getDeclaringClass().isInterface()
          || targetClass != method.getDeclaringClass())) {
        Class<?> declaringClass = method.getDeclaringClass();
        try {
          final String methodName = joinPoint.getSignature().getName();
          method = declaringClass.getDeclaredMethod(methodName, method.getParameterTypes());
          // is interface method annotated
          annotated = method.isAnnotationPresent(LogInfo.class);
        } catch (NoSuchMethodException | SecurityException e) {
          errorMethodException(LogUtil.class, e, joinPoint);
        }
      }
    }

    return annotated;
  }

  private String messageBefore(JoinPoint joinPoint) {
    return METHOD_ENTRY_ + getDescription(joinPoint);
  }

  private String messageAfter(JoinPoint joinPoint, Object result) {
    return StringReplacer.replaceNewLine(new StringBuilder(METHOD_EXIT_).append(getSignatureName(joinPoint))
        .append("; return value: ")
        .append(hideReturnValue(joinPoint) ? NOT_LOGGED : lessVerboze(result)).toString());
  }

  private boolean hideElement(final JoinPoint joinPoint, final Predicate<HideLogElements> hider) {
    final HideLogElements hle = Optional.ofNullable(joinPoint).map(JoinPoint::getTarget)
        .map(Object::getClass).map(c -> AnnotationUtils.findAnnotation(c, HideLogElements.class))
        .orElse(AnnotationUtils.findAnnotation(
            ((MethodSignature) joinPoint.getSignature()).getMethod(), HideLogElements.class));


    return hle != null && hider.test(hle);
  }

  private boolean hideReturnValue(final JoinPoint joinPoint) {
    return hideElement(joinPoint, HideLogElements::hideReturnValue);
  }

  private boolean hideParameters(final JoinPoint joinPoint) {
    return hideElement(joinPoint, HideLogElements::hideParameters);
  }

  private static Object lessVerbose(Object value, int maxItems) {
    Object reduced = value;
    if (value != null && Collection.class.isAssignableFrom(value.getClass())) {
      Collection<?> fromResult = (Collection<?>) value;
      if (fromResult.size() > maxItems) {
        reduced = new StringBuilder("Large entry of ").append(fromResult.size())
            .append(" items, reduced to first ").append(maxItems).append(" units: ")
            .append(fromResult.stream().limit(maxItems).collect(Collectors.toList()));
      }
      fromResult.stream().limit(maxItems).collect(Collectors.toList());
    }
    return reduced;

  }

  private Object lessVerboze(Object value) {
    return lessVerbose(value, COLLECTION_LOG_LIMIT);
  }

  /**
   * AOP logging of normal method exit
   * deprecated. @AfterReturning is superseded with @Around("loggingPointcut()") as latter allows to log method duration
   * @param joinPoint method join point
   * @param result method return value
   */
  public void logAfterReturning(JoinPoint joinPoint, Object result) {
    Logger logger = getLogger(joinPoint);
    boolean info = isInfoEnabled(logger) && infoAnnotated(joinPoint);
    if (info) {
      logger.info(messageAfter(joinPoint, result));
    } else if (isDebugEnabled(logger)) {
      logger.debug(messageAfter(joinPoint, result));
    }
  }

  /**
   * AOP logging of exceptional method exit
   * deprecated. @AfterThrowing is superseded with @Around("loggingPointcut()") as latter allows
   * to log method duration
   * @param joinPoint method join point
   * @param e exception caused method exit
   */
  public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
    final Logger logger = getLogger(joinPoint);
    final boolean hideStackTrace = isExclusion(e.getClass(), exceptionLogStacktraceHideClasses);

    Consumer<String> verboseLogger = null;
    Consumer<String> digestLogger = null;

    if (isExclusion(e.getClass(), exceptionLogInfoClasses) && isInfoEnabled(logger)) {
      // note different (overloaded) logger::info for different values of hideStackTrace
      if (hideStackTrace) {
        digestLogger = logger::info;
      } else {
        verboseLogger = logger::info;
      }
    } else if (isExclusion(e.getClass(), exceptionLogWarnClasses) && isWarnEnabled(logger)) {
      if (hideStackTrace) {
        digestLogger = logger::warn;
      } else {
        verboseLogger = logger::warn;
      }
    } else if (isErrorEnabled(logger)) {
      if (hideStackTrace) {
        digestLogger = logger::error;
      } else {
        verboseLogger = logger::error;
      }
    } else {
      return;
    }
    logAdvicedWhenException(verboseLogger, digestLogger, joinPoint, e);
  }

  private void logAdvicedWhenException(Consumer<String> verboseLogger,
      Consumer<String> digestLogger, JoinPoint joinPoint, Throwable e) {
    StringBuilder message =
        new StringBuilder(METHOD_EXIT_WITH_EXCEPTION).append(getDescription(joinPoint));
    if (digestLogger != null) {
      digestLogger.accept(StringReplacer
          .replaceNewLine(message.append(COLON).append(SPACE).append(e.getMessage()).toString()));
    } else if (verboseLogger != null) {
      verboseLogger.accept(StringReplacer.throwableMessage(message.toString(), e));
    }
  }

  /**
   * AOP logging around public methods
   * @param joinPoint method's join point
   * @return method's return value
   * @throws Throwable exception thrown by method
   */
  @Around("loggingPointcut()")
  public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
    Instant startTime = null;
    final Logger logger = getLogger(joinPoint);
    boolean info = isInfoEnabled(logger) && autoInfo(joinPoint);
    boolean debug = isDebugEnabled(logger);

    try {
      if (info || debug) {
        infoOrDebug(logger, info, debug, messageBefore(joinPoint));
        if (logMethodDuration) {
          startTime = Instant.now();
        }
      }

      Object result = joinPoint.proceed();

      if (info || debug) {
        infoOrDebug(logger, info, debug, messageAfter(joinPoint, result));
      }

      return result;
    } catch (Throwable e) {
      if (info || debug) {
        logAfterThrowing(joinPoint, e);
      }
      throw e;
    } finally {
      if (logMethodDuration && (info || debug)) {
        infoOrDebug(logger, info, debug,
            methodDurationMessage(getSignatureName(joinPoint), startTime));
      }
    }
  }

  private boolean autoInfo(JoinPoint joinPoint) {
    return (infoController && autoInfo(joinPoint, CLASS_POSTFIX_CONTROLLER))
        || (infoClient && autoInfo(joinPoint, CLASS_POSTFIX_CLIENT)) || infoAnnotated(joinPoint);
  }

  private boolean autoInfo(JoinPoint joinPoint, String classNamePostfix) {
    return Optional.ofNullable(joinPoint).map(jp -> jp.getTarget()).map(t -> t.getClass())
        .map(c -> c.getSimpleName()).filter(s -> s.endsWith(classNamePostfix)).isPresent();
  }

  private String getDescription(JoinPoint joinPoint) {
    MethodSignature ms = (MethodSignature) joinPoint.getSignature();
    String[] paramNames = ms.getParameterNames();
    Object[] params = joinPoint.getArgs();
    StringBuilder sb = new StringBuilder(getSignatureName(joinPoint))
        .append(hasCount(paramNames) ? ("; parameter names- " + Arrays.toString(paramNames)) : "")
        .append(
            hasCount(params)
                ? "; parameters- "
                    + (hideParameters(joinPoint) ? NOT_LOGGED : Arrays.deepToString(params))
                : "");
    return StringReplacer.replaceNewLine(sb.toString());
  }

  private String getSignatureName(JoinPoint joinPoint) {
    MethodSignature ms = ((MethodSignature) joinPoint.getSignature());
    return joinPoint.getTarget().getClass().getName().contains("com.sun.proxy")
        // This is to log Sun proxies of Feign clients, Spring JPA's, etc.
        ? new StringBuilder(ms.getDeclaringTypeName()).append("::").append(ms.getName()).toString()
        : ms.getName();
    /**
     * getName() provides nice output for commons-logging. joinPoint.getSignature().toString() gives
     * a bit clumsy output for some log4j settings
     */
  }

  private Logger getLogger(JoinPoint joinPoint) {
    return getLogger(joinPoint.getTarget().getClass());
  }

}
