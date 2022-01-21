@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  change-vector-collector startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Add default JVM options here. You can also use JAVA_OPTS and CHANGE_VECTOR_COLLECTOR_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\change-vector-collector.jar;%APP_HOME%\lib\commons-math3-3.6.1.jar;%APP_HOME%\lib\org.eclipse.jgit-5.0.1.201806211838-r.jar;%APP_HOME%\lib\commons-csv-1.6.jar;%APP_HOME%\lib\commons-text-1.8.jar;%APP_HOME%\lib\commons-cli-1.4.jar;%APP_HOME%\lib\commons-collections4-4.4.jar;%APP_HOME%\lib\commons-io-2.5.jar;%APP_HOME%\lib\json-20190722.jar;%APP_HOME%\lib\weka-dev-3.9.3.jar;%APP_HOME%\lib\client-2.1.2.jar;%APP_HOME%\lib\gen.jdt-2.1.2.jar;%APP_HOME%\lib\slf4j-simple-1.6.1.jar;%APP_HOME%\lib\core-2.1.2.jar;%APP_HOME%\lib\simmetrics-core-3.2.3.jar;%APP_HOME%\lib\guava-28.0-jre.jar;%APP_HOME%\lib\jsch-0.1.54.jar;%APP_HOME%\lib\jzlib-1.1.1.jar;%APP_HOME%\lib\JavaEWAH-1.1.6.jar;%APP_HOME%\lib\httpclient-4.5.2.jar;%APP_HOME%\lib\slf4j-api-1.7.2.jar;%APP_HOME%\lib\commons-lang3-3.9.jar;%APP_HOME%\lib\java-cup-11b-2015.03.26.jar;%APP_HOME%\lib\java-cup-11b-runtime-2015.03.26.jar;%APP_HOME%\lib\bounce-0.18.jar;%APP_HOME%\lib\mtj-1.0.4.jar;%APP_HOME%\lib\all-1.1.2.pom;%APP_HOME%\lib\netlib-native_ref-osx-x86_64-1.1-natives.jar;%APP_HOME%\lib\netlib-native_ref-linux-x86_64-1.1-natives.jar;%APP_HOME%\lib\netlib-native_ref-linux-i686-1.1-natives.jar;%APP_HOME%\lib\netlib-native_ref-win-x86_64-1.1-natives.jar;%APP_HOME%\lib\netlib-native_ref-win-i686-1.1-natives.jar;%APP_HOME%\lib\netlib-native_ref-linux-armhf-1.1-natives.jar;%APP_HOME%\lib\netlib-native_system-osx-x86_64-1.1-natives.jar;%APP_HOME%\lib\netlib-native_system-linux-x86_64-1.1-natives.jar;%APP_HOME%\lib\netlib-native_system-linux-i686-1.1-natives.jar;%APP_HOME%\lib\netlib-native_system-linux-armhf-1.1-natives.jar;%APP_HOME%\lib\netlib-native_system-win-x86_64-1.1-natives.jar;%APP_HOME%\lib\netlib-native_system-win-i686-1.1-natives.jar;%APP_HOME%\lib\native_ref-java-1.1.jar;%APP_HOME%\lib\native_system-java-1.1.jar;%APP_HOME%\lib\netlib-java-1.1.jar;%APP_HOME%\lib\core-1.1.2.jar;%APP_HOME%\lib\arpack_combined_all-0.1.jar;%APP_HOME%\lib\classindex-3.4.jar;%APP_HOME%\lib\org.eclipse.jdt.core-3.16.0.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\checker-qual-2.8.1.jar;%APP_HOME%\lib\error_prone_annotations-2.3.2.jar;%APP_HOME%\lib\j2objc-annotations-1.3.jar;%APP_HOME%\lib\animal-sniffer-annotations-1.17.jar;%APP_HOME%\lib\httpcore-4.4.4.jar;%APP_HOME%\lib\commons-logging-1.2.jar;%APP_HOME%\lib\commons-codec-1.10.jar;%APP_HOME%\lib\trove4j-3.0.3.jar;%APP_HOME%\lib\gson-2.8.2.jar;%APP_HOME%\lib\jgrapht-core-1.0.1.jar;%APP_HOME%\lib\org.eclipse.core.resources-3.15.100.jar;%APP_HOME%\lib\org.eclipse.text-3.12.0.jar;%APP_HOME%\lib\org.eclipse.core.expressions-3.8.0.jar;%APP_HOME%\lib\org.eclipse.core.runtime-3.23.0.jar;%APP_HOME%\lib\org.eclipse.core.filesystem-1.9.100.jar;%APP_HOME%\lib\org.eclipse.osgi-3.17.0.jar;%APP_HOME%\lib\org.eclipse.core.jobs-3.12.0.jar;%APP_HOME%\lib\org.eclipse.core.contenttype-3.8.0.jar;%APP_HOME%\lib\org.eclipse.equinox.app-1.6.0.jar;%APP_HOME%\lib\org.eclipse.equinox.registry-3.11.0.jar;%APP_HOME%\lib\org.eclipse.equinox.preferences-3.9.0.jar;%APP_HOME%\lib\org.eclipse.core.commands-3.10.100.jar;%APP_HOME%\lib\org.eclipse.equinox.common-3.15.0.jar;%APP_HOME%\lib\jniloader-1.1.jar

@rem Execute change-vector-collector
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %CHANGE_VECTOR_COLLECTOR_OPTS%  -classpath "%CLASSPATH%" change.vector.collector.Main %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable CHANGE_VECTOR_COLLECTOR_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%CHANGE_VECTOR_COLLECTOR_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
