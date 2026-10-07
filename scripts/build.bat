@echo off
rem Compile every experiment together with common\src. Output: build\exp0 ... build\exp7
rem NOT TESTED on Windows by the author; keep the project path free of spaces.
setlocal
cd /d "%~dp0\.."
for %%N in (0 1 2 3 4 5 6 7) do (
    for /d %%D in (exp%%N-*) do (
        if exist build\exp%%N rmdir /s /q build\exp%%N
        mkdir build\exp%%N
        dir /s /b common\src\*.java %%D\src\*.java > build\sources%%N.txt
        javac -d build\exp%%N @build\sources%%N.txt || exit /b 1
        echo compiled exp%%N
    )
)
endlocal
