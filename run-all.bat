@echo off
rem Windows helper: compiles all experiments and runs the demos that need only ONE terminal.
rem (Exp 0, 1 and 4 need several terminals - follow the README in those folders.)
rem NOT TESTED on Windows by the author of this project; use the .sh scripts on Linux/macOS/WSL.
rem Keep the project in a folder path WITHOUT spaces.
setlocal
cd /d "%~dp0"
call scripts\build.bat || exit /b 1
for %%C in (exp2.ConcurrencyDemo exp2.RaceConditionDemo exp3.Exp3Demo exp5.Exp5Demo exp6.Exp6Demo exp7.Exp7Demo) do (
    echo.
    echo ===== Running %%C =====
    for /f "tokens=1 delims=." %%E in ("%%C") do java -cp build\%%E %%C
)
endlocal
