@echo off
setlocal
title AANO Local Build & Auto-Cleaner
echo =======================================================
echo   AANO Windows Setup & Android APK Local Builder
echo =======================================================
cd /d "%~dp0"

python build_local.py %*

echo.
echo =======================================================
echo   Build process finished.
echo =======================================================
pause
