@echo off

set DIR=%~dp0..
set APP_HOME=%DIR%

java -cp "%DIR%\lib\*" -Dmatchbox.plugins.dir="%APP_HOME%\plugins" fr.cnrs.lacito.matchbox.Starter