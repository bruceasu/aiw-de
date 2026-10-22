@echo off
setlocal DisableDelayedExpansion

rem Resolve the project root independently of the caller's working directory.
pushd "%~dp0.." || exit /b 1

rem Load literal NAME=value entries without delayed expansion or command execution.
if exist ".env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
)

if not defined DEBUG_ADDRESS set "DEBUG_ADDRESS=127.0.0.1:5005"
if not defined DEBUG_SUSPEND set "DEBUG_SUSPEND=n"
if not "%DEBUG_SUSPEND%"=="y" if not "%DEBUG_SUSPEND%"=="n" (
    >&2 echo DEBUG_SUSPEND must be y or n
    popd
    exit /b 1
)

set "startup_jvm_arguments=%STARTUP_JVM_ARGS%"
if defined startup_jvm_arguments set "startup_jvm_arguments=%startup_jvm_arguments% "
set "startup_jvm_arguments=%startup_jvm_arguments%-agentlib:jdwp=transport=dt_socket,server=y,suspend=%DEBUG_SUSPEND%,address=%DEBUG_ADDRESS%"

echo Starting Spring Boot with JDWP at %DEBUG_ADDRESS% ^(suspend=%DEBUG_SUSPEND%^)
call mvn clean spring-boot:run "-Dspring-boot.run.jvmArguments=%startup_jvm_arguments%" %*
set "startup_exit_code=%ERRORLEVEL%"
popd
endlocal & exit /b %startup_exit_code%
