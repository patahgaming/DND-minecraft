@echo off
REM ============================================
REM DnD Minecraft Test Server
REM Paper 1.21.11 + Geyser (Bedrock) + Floodgate
REM Java 25 Optimized
REM ============================================

set JAVA="java"
set JVM_MEM=-Xmx4G -Xms1G
set JVM_GC=-XX:+UseZGC -XX:+ZGenerational

REM Paper Performance Flags
set JVM_FLAGS=%JVM_MEM% %JVM_GC% ^
  -Dpaper.disableChannelLimit=true ^
  -Dpaper.playerconnection.keepalive=120 ^
  -Dterminal.jline=false ^
  -Dterminal.ansi=true ^
  -XX:+PerfDisableSharedMem ^
  -XX:+UseStringDeduplication ^
  -XX:+OptimizeStringConcat ^
  -XX:+AlwaysPreTouch ^
  -XX:+ParallelRefProcEnabled ^
  -XX:+DisableExplicitGC ^
  -XX:MaxGCPauseMillis=200 ^
  -XX:GCTimeRatio=99

echo.
echo ========================================
echo   DnD Minecraft — Test Server
echo ========================================
echo   Paper 1.21.11 + Java 25 ZGC
echo   Geyser (Bedrock on port 19132)
echo   Floodgate (auto-auth)
echo   DnDPlugin v1.0.0.58
echo   Spark Profiler
echo ========================================
echo.

echo Starting server...
%JAVA% %JVM_FLAGS% --enable-native-access=ALL-UNNAMED -jar paper.jar nogui
pause
