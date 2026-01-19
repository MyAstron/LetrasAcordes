# 🎸 Melodias: Letras y Acordes (v5.1.0)
**Más que un visor de acordes: Una estación de trabajo inteligente para la interpretación en vivo.**

[![Kotlin](https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)
[![Stage Ready](https://img.shields.io/badge/Stage-Ready-yellow?style=for-the-badge)](https://github.com/MyAstron/LetrasAcordes)

**Melodias** es una potente estación de trabajo digital para músicos. No es solo un visor de documentos; es un ecosistema inteligente que adapta el contenido musical (letras y acordes) a las necesidades técnicas del intérprete en tiempo real.

---

## 🏛️ Arquitectura del Sistema (Conceptos de Ingeniería)

### 1. El Salón de Repertorio (Gestión de Datos y Galería Visual)
Funciona como el **Single Source of Truth** visual, evolucionando de un catálogo de texto a una **Galería Visual Dinámica**. Un `LazyColumn` reactivo observa un `StateFlow` del repositorio de Room, ahora enriquecido con portadas automáticas vía **iTunes API**. El sistema realiza búsquedas silenciosas para asignar el arte original de cada obra, mientras un motor de procesamiento eficiente redimensiona cargas manuales a **250x250px**, optimizando RAM y almacenamiento sin sacrificar la estética.

### 2. El Taller de Luthería (Configuración y Perfiles)
Este es el **Backstage** técnico de la aplicación. Aquí, el músico elige su "traje": la interfaz muta para entregar afinadores cromáticos y diagramas si eres Guitarrista, o simplifica las herramientas si eres Cantante. Implementa un sistema de importación/exportación GZIP que ahora es más inteligente: los respaldos `.la` se mantienen ligeros al omitir datos temporales de internet, asegurando una portabilidad total y limpia.

### 3. El Estudio de Ensayo (Renderizado Dinámico)
Actúa como un atril inteligente impulsado por un `SongTextFormatter` que utiliza **Regex** para separar la armonía de la lírica. Este atril digital permite transposición en tiempo real, **Auto-scroll** de precisión y un metrónomo vinculado al ciclo de vida del componente, asegurando que el ritmo nunca se pierda, independientemente de la complejidad de la obra.

### 4. La Mesa de Composición (Validación Armónica)
Es el escritorio del compositor, reforzado con **Ingeniería de Integridad**. El editor valida en tiempo real la estructura de la obra, impidiendo el anidamiento erróneo de bloques instrumentales y garantizando la unicidad de secciones críticas como `INTRO`, `FINAL` o `CÍRCULO`. Transforma ideas volátiles en entidades estructuradas y persistidas en SQLite con metadatos de tonalidad y ritmo detectados automáticamente.

### 5. La Organización del Show (Setlist Management)
Diseñada para modelar la energía de un concierto, esta sección permite una **Curaduría por Bloques**. Mediante un sistema de **Checkboxes**, el músico puede armar repertorios masivos en segundos. Implementa un algoritmo de intercambio $O(n)$ para reordenamiento mediante **Drag & Drop** y un filtro de colisiones que oculta automáticamente listas ya agregadas, optimizando el diseño del setlist perfecto.

### 6. El Escenario (Modo Presentación Blindado)
Es el foco directo bajo los reflectores, optimizado para paneles OLED en **Alto Contraste**. El modo ahora está blindado para el directo: bloquea salidas accidentales mediante la captura del botón "Atrás" físico y utiliza **Listas Efímeras** (`LISTA_TEMPORAL_AUTO`) que se auto-limpian al finalizar el show. Un nuevo panel modal de **Info Rápida** permite visualizar metadatos y arte de la canción con un toque sobre el título, manteniendo el flujo sin interrupciones.


## ✨ Funciones Core
* **OCR Inteligente:** Digitalización de partituras físicas mediante Google ML Kit.
* **Afinador Cromático:** Procesamiento de audio en tiempo real para asistencia técnica.
* **Motor de Impresión:** Generación de **PDF** profesionales que respetan escrupulosamente el orden manual del músico, incluyendo miniaturas e índice sincronizado.
* **Wakelock:** Gestión de energía para mantener la pantalla activa durante toda la presentación.

---

Pruebalo con **/app-debug.apk**<br>
Desarrollado por **Cristopher (MyAstron)**.
*© 2026 Click Doris / MyAstron*
