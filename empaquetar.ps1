# Genera el ejecutable de Windows (carpeta con SanMiguelito.exe y el entorno de Java incluido).
# Requisitos: JDK 21 (incluye jpackage) y Maven.
# Uso: abrir PowerShell en esta carpeta y ejecutar  .\empaquetar.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

mvn -q -B clean package
if ($LASTEXITCODE -ne 0) { throw "La compilación o las pruebas fallaron." }

$entrada = "target\entrada-jpackage"
New-Item -ItemType Directory -Force $entrada | Out-Null
Copy-Item target\sanmiguelito.jar $entrada
Copy-Item target\lib\*.jar $entrada

# Entorno de Java reducido: solo los módulos que usa la aplicación (calculados con jdeps) y los
# formatos de fecha y número en español. Así el ejecutable pesa mucho menos.
$runtime = "target\runtime"
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
jlink --add-modules java.base,java.desktop,java.sql,java.logging,java.xml,jdk.unsupported,jdk.jfr,jdk.localedata `
  --include-locales=es,en `
  --strip-debug --no-header-files --no-man-pages --compress=zip-9 `
  --output $runtime
if ($LASTEXITCODE -ne 0) { throw "jlink falló." }

jpackage --type app-image `
  --runtime-image $runtime `
  --name SanMiguelito `
  --app-version 1.0.0 `
  --vendor "Cacharreria San Miguelito" `
  --description "Sistema de gestion de ventas e inventario" `
  --icon src\main\icono.ico `
  --input $entrada `
  --main-jar sanmiguelito.jar `
  --main-class com.sanmiguelito.Launcher `
  --java-options "-Dfile.encoding=UTF-8" `
  --dest target\dist
if ($LASTEXITCODE -ne 0) { throw "jpackage falló." }

Compress-Archive -Path target\dist\SanMiguelito -DestinationPath target\SanMiguelito_v1.0_Windows.zip -Force
Write-Host "Listo: target\dist\SanMiguelito\SanMiguelito.exe y target\SanMiguelito_v1.0_Windows.zip"
