# 🎸 Melodias: Letras y Acordes (v4.0.1)
**Más que un visor de acordes: Una estación de trabajo inteligente para la interpretación en vivo.**

[![Kotlin](https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)
[![Stage Ready](https://img.shields.io/badge/Stage-Ready-yellow?style=for-the-badge)](https://github.com/MyAstron/LetrasAcordes)

## 🎼 ¿Qué es "Melodias"?
Melodias es una **Workstation Digital** diseñada para músicos profesionales y en formación. A diferencia de un lector de documentos estático, esta aplicación actúa como un **Asistente de Escenario** que adapta el contenido musical a las necesidades técnicas y físicas del intérprete en tiempo real.

---

## 🛠️ Innovaciones y Tecnologías Implementadas

### 1. Ecosistema de Intervención Dinámica
La app procesa el texto musical "al vuelo" mediante un motor de renderizado que permite:
* **Transposición Cromática:** Recálculo de acordes mediante lógica matemática modular.
* **Modos de Rol (UI Adaptativa):** La interfaz muta según el usuario sea **GUITARRISTA** (muestra diagramas de acordes) o **CANTANTE** (maximiza la legibilidad del texto).
* **Modo Escenario:** Esquema de alto contraste (Negro/Amarillo) diseñado para evitar deslumbramientos y fatiga visual en entornos oscuros o con luces de escenario.

### 2. Sistemas Críticos de Interpretación
* **Gestión de Energía (Wakelock):** Implementación de `FLAG_KEEP_SCREEN_ON` para garantizar que la pantalla permanezca activa durante toda la ejecución musical.
* **Motor de Auto-Scroll Pro:** Desplazamiento automatizado sincronizado (~60 FPS) que permite al músico tocar sin interrupciones manuales.
* **Afinador Cromático Integrado:** Procesamiento de señales de audio en tiempo real para capturar frecuencias (E2 a E4) y asistir en la afinación del instrumento.
* **Metrónomo Inteligente:** Estimación automática de BPM basada en etiquetas de "Ritmo" detectadas en la canción.

### 3. Visión Artificial y Digitalización (OCR)
Integración con **Google ML Kit** para transformar partituras físicas en código digital interactivo:
* **The Merger:** Algoritmo de visión que fusiona líneas de texto y acordes manteniendo la alineación vertical original mediante análisis de `BoundingBox`.
* **Normalización de Etiquetas:** Limpieza automática de texto para estandarizar secciones como `[INTRO]`, `[CORO]` y `[PUENTE]`.

---

## 🏗️ Arquitectura de Software
* **MVVM & Clean Architecture:** Separación estricta de lógica de negocio y UI.
* **State Management Avanzado:** Uso de `StateFlow`, `mutableStateMapOf` y `LaunchedEffect` con claves dinámicas para una reactividad de alto rendimiento.
* **Persistencia Local (Room):** Base de datos robusta para funcionamiento 100% offline.
* **Protocolo de Archivos .la:** Sistema propietario de exportación/importación serializado en JSON y comprimido para el respaldo de repertorios.

---
**Desarrollado por Cristopher (MyAstron)** *Ingeniería de Software enfocada en soluciones para el mundo real.*