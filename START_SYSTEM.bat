@echo off
chcp 65001 >nul
title KHOI DONG HE THONG STRONGWINE
cd /d "%~dp0"

echo ======================================================================
echo                  KHOI DONG HE THONG STRONGWINE
echo ======================================================================
echo.

:: 1. Kiem tra va bat dich vu SQL Server (SQLEXPRESS)
echo [1/2] Kiem tra trang thai Microsoft SQL Server (SQLEXPRESS)...
sc query MSSQL$SQLEXPRESS | find /i "RUNNING" >nul 2>&1
if %errorlevel% neq 0 (
    echo Dich vu chua chay. Dang yeu cau quyen Administrator de bat...
    net session >nul 2>&1
    if %errorlevel% neq 0 (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
        exit /b
    )
    net start MSSQL$SQLEXPRESS
) else (
    echo [OK] SQL Server dang chay san sang.
)

echo.
:: 2. Khoi dong ung dung Spring Boot
echo [2/2] Dang khoi dong StrongWine Spring Boot tai http://localhost:8080 ...
start "StrongWine Spring Boot Server" cmd /k "cd /d ""%~dp0"" && mvnw.cmd spring-boot:run"

echo.
echo ======================================================================
echo                     HE THONG DANG KHOI DONG
echo ======================================================================
echo   Trang chu khach hang: http://localhost:8080
echo   Trang quan tri Admin: http://localhost:8080/admin
echo   Buong lai Shipper:    http://localhost:8080/shipper/dashboard
echo ----------------------------------------------------------------------
echo   Tai khoan mau:
echo     - Admin:   admin           / admin123
echo     - User:    demo            / demo123
echo     - Shipper: shipper.ops.hcm / demo123
echo ======================================================================
echo.
echo Cua so may chu Spring Boot da duoc mo rieng biet.
echo Nhan phim bat ky de thoat cua so dieu khien nay...
pause >nul

