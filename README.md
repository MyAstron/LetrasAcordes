# 🎸 Melodias: Letras y Acordes (v4.2.3)
**Más que un visor de acordes: Una estación de trabajo inteligente para la interpretación en vivo.**

[![Kotlin](https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)
[![Stage Ready](https://img.shields.io/badge/Stage-Ready-yellow?style=for-the-badge)](https://github.com/MyAstron/LetrasAcordes)

**Melodias** es una potente estación de trabajo digital para músicos. No es solo un visor de documentos; es un ecosistema inteligente que adapta el contenido musical (letras y acordes) a las necesidades técnicas del intérprete en tiempo real.

---

## 🏛️ Arquitectura del Sistema (Conceptos de Ingeniería)

### 1. El Salón de Repertorio (Gestión de Datos)
Funciona como el **Single Source of Truth** visual, una biblioteca infinita donde un `LazyColumn` reactivo observa un `StateFlow` del repositorio de Room. Es un sistema tan eficiente que el "bibliotecario digital" encuentra cualquier obra mediante operadores funcionales de filtrado antes de que termines de escribir el título, permitiendo organizar estantes temáticos (categorías) que puedes mover o clavar en la pared de tu colección a tu antojo para que crezca sin perder jamás el orden.

### 2. El Taller de Luthería (Configuración y Perfiles)
Este es el **Backstage** técnico de la aplicación, encargado de gestionar el estado global y las preferencias mediante `SharedPreferences`. Aquí es donde el músico elige su "traje": mediante lógica condicional, la app muta su interfaz para entregarte afinadores y diagramas si eres Guitarrista, o guarda las herramientas pesadas si decides ser Cantante. Es el lugar donde se transforman los datos en flujos de bytes GZIP para asegurar que tu diario musical tenga una portabilidad total y nunca se pierda en el tiempo.

### 3. El Estudio de Ensayo (Renderizado Dinámico)
Actúa como un atril inteligente impulsado por un `SongTextFormatter` que utiliza expresiones regulares (**Regex**) para separar la letra de la armonía. Esta pantalla no solo sostiene la partitura; puede cambiar la tonalidad de toda la obra con un chasquido de dedos mediante su motor de transposición en tiempo real, mientras un asistente de **Auto-scroll** mueve la hoja por ti y un metrónomo vinculado al ciclo de vida del `DisposableEffect` marca el pulso invisible de tu práctica para que nunca dejes de tocar.

### 4. La Mesa de Composición (Input y Persistencia)
Es el escritorio del compositor, un espacio en blanco donde se digitaliza la inspiración validando la integridad de cada entrada. El sistema analiza el texto crudo para generar metadatos automáticos —como la detección del tono original— y transforma una idea volátil en una entidad de `Cancion` estructurada y persistida en **SQLite**, marcando versos, puentes y estribillos para que el sistema aprenda a leer y entender tu propia música.

### 5. La Organización del Show (Setlist Management)
Diseñada para modelar la montaña rusa de emociones de un concierto, esta sección implementa un algoritmo de intercambio de posición $O(n)$ que permite un reordenamiento manual mediante gestos de **Drag & Drop**. Imagina mover fotos sobre una mesa con total libertad; el sistema utiliza una lista mutable efímera para garantizar fluidez visual y realiza una persistencia atómica por lotes (**Batch Update**) solo al finalizar, optimizando el rendimiento del dispositivo mientras diseñas tu setlist perfecto.

### 6. El Escenario (Modo Presentación)
Es el foco directo bajo los reflectores: una variante de **Alto Contraste** optimizada para paneles OLED (Pure Black) que elimina cualquier ruido visual para dejarte solo con la música. Funciona como un visor nocturno que resalta los acordes en amarillo neón para garantizar la legibilidad en la oscuridad de un escenario, utilizando una navegación secuencial basada en índices para que la siguiente canción esté a un solo toque de distancia, permitiendo que el show nunca se detenga.


## ✨ Funciones Core
* **OCR Inteligente:** Digitalización de partituras físicas mediante Google ML Kit.
* **Afinador Cromático:** Procesamiento de audio en tiempo real para asistencia técnica.
* **Exportación PDF:** Generación de cancioneros profesionales con modo compacto e índice automático.
* **Wakelock:** Gestión de energía para mantener la pantalla activa durante toda la presentación.

---

Pruebalo con **/debug.apk**<br>
Desarrollado por **Cristopher (MyAstron)**.
*© 2026 Click Doris / MyAstron*