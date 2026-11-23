# Sistema de Gestión de Ventas e Inventario — Cacharrería San Miguelito

Aplicación de escritorio (versión Beta 1.0) desarrollada para la asignatura Ingeniería de Software I (UPTC, 2026) por
Karina Lucero Alfonso Valderrama, Yirman Alejandro Fonseca Suarez y Juan Esteban García Álvarez.

## Tecnologías

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje principal |
| JavaFX 21 + CSS | Interfaz gráfica con los colores del manual de estilos de la empresa |
| Jackson | Lectura y escritura de los archivos JSON (arquitectura de servidor de archivos) |
| OpenPDF | Comprobante de venta y reportes en PDF |
| JUnit 5 | 18 pruebas automáticas de las reglas del negocio |
| Maven | Dependencias y construcción |
| jpackage | Ejecutable de Windows con Java incluido |

## Estructura

```
src/main/java/com/sanmiguelito/
  modelo/     Entidades: Usuario, Producto, Movimiento, Venta, ItemVenta, Rol, TipoMovimiento
  datos/      AlmacenJson (guardado atómico con archivo temporal) y Repositorio (almacenes D1–D5)
  servicio/   Reglas del negocio: usuarios (PBKDF2), productos, inventario, ventas, reportes PDF, respaldos
  ui/         Pantallas JavaFX: ingreso, inicio, ventas, productos, entradas y salidas, movimientos,
              reportes, usuarios, respaldos y cambio de contraseña
src/main/resources/  estilos.css, logo e ícono
src/test/java/       ServiciosTest (pruebas) y Capturas (genera las imágenes de la documentación)
```

## Trazabilidad con los requisitos

| Requisito | Implementación |
|---|---|
| RF-001 Ingreso | `ServicioUsuarios.autenticar`, `VistaLogin` |
| RF-002 a RF-006 Productos | `ServicioProductos`, `VistaProductos` |
| RF-007, RF-008 Entradas y salidas | `ServicioInventario`, `VistaInventario` |
| RF-009 Validación de existencias | `ServicioVentas.Carrito.agregar`, `ServicioVentas.confirmar`, `ServicioInventario` |
| RF-010 Disponibilidad | `Producto.estadoExistencias`, `ServicioProductos.pocasExistencias` |
| RF-011 Movimientos | `ServicioInventario.consultar`, `VistaMovimientos` |
| RF-012 Usuarios y roles | `ServicioUsuarios`, `VistaUsuarios`, `Contexto.exigirAdministrador` |
| RF-013 Ventas | `ServicioVentas`, `VistaVentas` |
| RF-014 Comprobante | `ServicioReportes.comprobante` |
| RF-015 Reportes | `ServicioReportes`, `VistaReportes` |
| RNF-002 Seguridad | `Seguridad` (PBKDF2 con sal) |
| RNF-005 Integridad | `AlmacenJson.guardar` (temporal + movimiento atómico), validaciones de los servicios |
| RNF-006 Respaldo | `ServicioRespaldos`, `VistaRespaldos` |

## Compilar, probar y ejecutar

Requisitos: JDK 21 y Maven 3.9.

```
mvn clean package          # compila y ejecuta las 18 pruebas
.\empaquetar.ps1           # genera target\dist\SanMiguelito\SanMiguelito.exe y el .zip
```

Los datos se guardan en `%USERPROFILE%\SanMiguelito` (carpetas `datos`, `respaldos` y `documentos`).
En el primer uso se crean los usuarios `admin` / `SanMiguelito2026` (administrador) y `vendedor` / `Vendedor2026`
(vendedor), y un catálogo de productos de ejemplo. Cambie las contraseñas al iniciar el uso real.
