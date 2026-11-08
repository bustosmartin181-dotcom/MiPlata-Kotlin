# Mi plata (Kotlin)

App Android nativa para administrar el dinero día a día.
Versión nueva de "Mi plata", hecha desde cero en Kotlin + Jetpack Compose.

## Qué hace
- Reparte la plata del período en un monto por día.
- Lo que sobra hoy pasa a mañana; si te pasás, se descuenta mañana.
- Fondo de ahorro con rendimiento estimado (tasa de Mercado Pago).
- Gastos importantes, entradas y "Me deben".
- Widget y ventanita para anotar gastos sin abrir la app (próximamente).
- Importa la copia de seguridad (JSON) de la versión anterior.

## Cómo se compila
El APK se arma solo con GitHub Actions al subir cambios a `main`.
Se descarga desde la pestaña **Actions** → última ejecución → **Artifacts** → `MiPlataK-APK`.
Siempre se firma con la misma clave (guardada en los secretos del repo `MIPLATA_KEYSTORE` y `MIPLATA_CLAVE`), así cada versión se instala encima de la anterior.

## Cómo se comprueban las cuentas
El motor (`app/src/main/java/com/miplata/kotlin/nucleo/`) es una traducción fiel del núcleo de Mi plata v9.9.36.
`herramientas/generar_casos.js` corre el `index.html` original sobre miles de situaciones y guarda los resultados;
la prueba `NucleoContraOriginalTest` exige que Kotlin dé exactamente lo mismo. Se corre sola antes de cada APK.

## Estado
- ✅ Etapa 1: motor de cálculo verificado (1.552 casos inventados + copias reales, todos iguales al original).
- ⏳ Etapa 2: pantallas Hoy, Plan, Días y Ajustes.
- ⏳ Etapa 3: guardar, anotar gastos, copias de seguridad.
- ⏳ Etapa 4: widget y ventanita flotante.

Autor: Martín Bustos
