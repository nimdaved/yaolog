yaolog 
=
These are yet another opinionated (yao) logging utility libraries. The library name is inspired by the ant naming heritage (another neat tool) and opinionated nature of modern logging frameworks. The motivation is to reduce logging related clutter in the code and still have appropriate  logging.
		
		
Description:
=
There are two libraries included: yaolog-config and yaolog-util
1. yaolog-config is opinionated configuration of spring boot project with logback. It spares innocent user from inclusion of spring-logback.xml and decision of what to log in every project by inclusion of this library.

2. yaolog-util is opinionated way of logging exceptions and method entry/exit points

Usage examples:
1. Debug method name, parameters and return values on entry/exit of any public method. Do nothing, you are already covered :-)
2. If you need to info for those just use @LogInfo annotation 
3. If your methods are not public, then use logMetodEntry(..) and logMethodExit(..)
4. If you need to log your exceptions with more context  then use errorMethodException(..) like this:

5. My all times favorite is errorWrapThrow(..). Try to guess what is doing from it's name. Example:
try {
	return provider.provide(input);
} catch (JobExecutionException | RuntimeException e) {
				LogUtil.errorWrapThrow(this, e, AsyncExecutionException.class, input);
}

Yes, all this is at performance costs. Yes, it could be more flexible. Yes, it is not applicable for each and every case. Yes, it is shipped without "best practices" sticker. Yes, it is opinionated.


IDE and Tools:
=
The code has been developed using jdk 1.8 and Gradle tool.

 

