# Скрипт для остановки процессов Gradle
Write-Host "Остановка процессов Gradle..." -ForegroundColor Yellow

# Остановить все процессы Java
Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
Write-Host "Процессы Java остановлены" -ForegroundColor Green

# Остановить градле демонов
Write-Host "Остановка Gradle daemon..."
./gradlew --stop 2>$null

# Удалить папку build если она заблокирована
if (Test-Path "app\build") {
    Write-Host "Удаление папки build..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
}

Write-Host "Готово! Все процессы Gradle остановлены." -ForegroundColor Green
