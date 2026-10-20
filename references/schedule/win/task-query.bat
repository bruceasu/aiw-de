@echo off
if "%~1" == "" (
	schtasks.exe /query 
) else (
	schtasks.exe /query /tn "%~1" /v /fo list
)