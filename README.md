# 🎸 Melodias: Letras y Acordes (v6.2.0)
**Más que un visor de acordes: Una estación de trabajo inteligente para la interpretación en vivo.**

[![Kotlin](https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)
[![Stage Ready](https://img.shields.io/badge/Stage-Ready-yellow?style=for-the-badge)](https://github.com/MyAstron/LetrasAcordes)

**Melodias** es una potente estación de trabajo digital para músicos. No es solo un visor de documentos; es un ecosistema inteligente que adapta el contenido musical (letras y acordes) a las necesidades técnicas del intérprete en tiempo real.

---

## 🏛️ Arquitectura del Sistema (Conceptos de Ingeniería)

### 1. El Salón de Repertorio (Gestión de Datos y Galería Visual)
Funciona como el **Single Source of Truth** visual, evolucionando de un catálogo de texto a una **Galería Visual Dinámica**. Un `LazyColumn` reactivo observa un `StateFlow` del repositorio de Room. Ahora incluye un **Sistema de Favoritos Integrado**, permitiendo el marcado instantáneo de canciones mediante estrella para acceso rápido, centralizando la gestión de las obras más importantes.

### 2. El Taller de Luthería (Configuración y Perfiles)
Este es el **Backstage** técnico de la aplicación. Implementa un sistema de importación/exportación GZIP inteligente: los respaldos `.la` se mantienen ligeros al omitir datos temporales, asegurando una portabilidad total.

### 3. El Estudio de Ensayo (Renderizado Dinámico)
Actúa como un atril inteligente impulsado por un `SongTextFormatter` que utiliza **Regex** para separar la armonía de la lírica. Este atril digital permite transposición en tiempo real, **Auto-scroll** de precisión y un metrónomo vinculado al ciclo de vida del componente.

### 4. La Mesa de Composición (Validación Armónica)
Es el escritorio del compositor. El editor valida en tiempo real la estructura de la obra, garantizando la unicidad de secciones críticas como `INTRO`, `FINAL` o `CÍRCULO`.

### 5. La Organización del Show (Setlist Management)
Diseñada para modelar la energía de un concierto. Mediante un sistema de edición intuitivo, el músico puede armar repertorios masivos en segundos. Implementa un algoritmo de intercambio $O(n)$ para reordenamiento mediante **Drag & Drop** y un filtro de colisiones que oculta automáticamente listas ya agregadas.

### 6. El Escenario (Modo Presentación Blindado)
Modo optimizado para paneles OLED en **Alto Contraste**. Bloquea salidas accidentales mediante la captura del botón "Atrás" físico y utiliza **Listas Efímeras** que se auto-limpian al finalizar el show.

---

## 💡 Funciones Especiales (New Additions)

*   **Smart Widget:** Widget de escritorio redimensionable con acceso directo a **Presentación Rápida**. El widget se sincroniza con tus listas de favoritos o personalizadas, permitiendo iniciar un concierto con un solo toque desde la pantalla de inicio.
*   **Header Inteligente:** La interfaz principal cuenta con un *Collapsing Header* que se optimiza al hacer scroll, maximizando el espacio de búsqueda y mejorando la legibilidad.
*   **Gestión Dinámica de Categorías:** Modo de edición con filtrado inteligente que protege tus categorías base ("Favoritos" y "Todas") para evitar ediciones accidentales, ofreciendo una experiencia de usuario limpia y segura.

---

Pruebalo con **/app-debug.apk**<br>
Desarrollado por **Cristopher (MyAstron)**.
*© 2026 Click Doris / MyAstron*
