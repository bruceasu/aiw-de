@echo off
ZIP=%~1
if "%ZIP%" == "" (set "ZIP=HEAD.zip")
c:\green\7-zip\7z.exe a "%ZIP%" .ai/ .wt/
