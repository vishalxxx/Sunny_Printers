@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" %*
