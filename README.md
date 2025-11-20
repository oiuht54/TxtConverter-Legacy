# TxtConverter

[🇷🇺 Читать на русском](#-txtconverter-ru)

**TxtConverter** is a professional desktop utility designed to quickly and safely prepare project source code for analysis by Neural Networks (LLMs), archiving, or sharing in chats.

The application scans your project folder and creates an optimized single text file that is easy to feed into ChatGPT, Claude, DeepSeek, or Gemini.

<!-- Screenshots Side-by-Side -->
<p align="center">
  <img src="docs/screenshot_en.png" width="48%" alt="English Interface" />
  <img src="docs/screenshot_ru.png" width="48%" alt="Russian Interface" />
</p>

---

## 🌍 New: Multilingual Support
The application now fully supports **English** and **Russian** languages.
*   **First Run:** You will be prompted to select your preferred language.
*   **Settings:** You can change the language at any time using the Settings (⚙) menu.
*   **Persistence:** Your choice is saved automatically for future sessions.

---

## 🔥 Key Features

### 🧠 Optimization for LLMs (AI)
*   **Token Saving:** We replaced bulky separators with minimalistic headers (`--- FILE: Name.ext ---`). This allows fitting more useful code into the AI's context window.
*   **Smart Merging:** You can choose which files to include **fully** and which to keep as **stubs**.
    *   *Example:* If a file is found but not selected for merging, the report will contain: `(File content omitted for brevity...)`. This gives the AI context about the file's existence without wasting tokens on its content.

### ⚡ Efficiency & UX
*   **Smart Sorting:** The unified file is automatically named `_(ProjectName)_Full_Source_code.txt`. The `_` symbol ensures the file appears at the top of your file explorer.
*   **Modern UI:** A custom dark interface (High Contrast Dark Theme) styled like modern IDEs (VS Code / JetBrains).
*   **Feedback:** Built-in **Progress Bar** and status line allow real-time tracking of large project processing.

### 🛡️ Safety
*   **Non-Destructive:** The app **never** modifies your source files. All results are saved in a separate `_ConvertedToTxt` folder inside your project.
*   **Junk Ignoring:** Built-in presets automatically exclude system folders (`.git`, `node_modules`, `Library`, `target`, `.godot`), ensuring only clean code gets into the report.

### ⚙️ Flexibility
*   **Presets:** Ready-made settings for **Unity**, **Godot**, **Java (Maven/Gradle)**, **Web Frontend**.
*   **Structure Map:** Optional generation of a `_FileStructure.md` file, which draws a folder tree of your project for better context understanding.

---

## 🚀 How to Use

1.  Run `TxtConverter.exe`.
2.  (First time only) Select your language.
3.  Click **"Select..."** and choose your project's root folder.
4.  Choose a **Preset** (e.g., *Unity Engine* or *Godot Engine*). The app will auto-fill extensions and ignored folders.
5.  Click **"Rescan"** to find files.
6.  (Optional) Click **"Select Files..."** to check only the scripts you need in full. Others will be included as stubs.
7.  Ensure **"Generate Merged File"** is checked.
8.  Click the big blue button **"START CONVERSION"**.
9.  Once done, check the created `_ConvertedToTxt` folder.

---

## 🛠️ Build from Source

The project is built on **Java 21** and **JavaFX 21**. It uses a layered architecture separating UI and background Tasks.

### Requirements
*   JDK 21+
*   Apache Maven

### Build Commands

1.  **Clone:**
    ```bash
    git clone https://github.com/YourName/TxtConverter.git
    cd TxtConverter
    ```

2.  **Prepare Icons (Optional):**
    *   Place `icon.ico` in the project root.
    *   Place `icon.png` in `src/main/resources/TartarusCore/TxtConverter/`.

3.  **Build (Create .exe):**
    ```bash
    mvn clean package
    ```
    Maven will download dependencies, build a "fat-jar", and use `jpackage` to create a native executable image.

4.  **Result:**
    The ready-to-use application will be in: `target/jpackage/TxtConverter/`

---

<br>
<br>

# 🇷🇺 TxtConverter (RU)

**TxtConverter** — это профессиональная десктопная утилита для быстрой и безопасной подготовки исходного кода проектов к анализу нейросетями (LLM), архивации или отправке в чаты.

Приложение сканирует папку проекта и создает оптимизированный единый текстовый файл, который удобно "скармливать" ChatGPT, Claude или DeepSeek.

---

## 🌍 Новое: Мультиязычность
Приложение теперь полностью поддерживает **Русский** и **Английский** языки.
*   **Первый запуск:** Программа предложит выбрать удобный язык.
*   **Настройки:** Вы можете сменить язык в любой момент через меню Настроек (⚙).
*   **Память:** Ваш выбор сохраняется автоматически для будущих запусков.

---

## 🔥 Ключевые возможности

### 🧠 Оптимизация для LLM (ИИ)
*   **Экономия токенов:** Мы заменили громоздкие разделители на минималистичные заголовки (`--- FILE: Name.ext ---`). Это позволяет вместить больше полезного кода в контекстное окно нейросети.
*   **Умное слияние:** Вы можете выбрать, какие файлы включить в отчет **полностью**, а какие оставить в виде **заглушек**.
    *   *Пример:* Если файл не выбран для слияния, в отчете появится строка: `(Содержимое файла опущено для краткости...)`. Это дает ИИ контекст о существовании файла, не тратя токены на его содержимое.

### ⚡ Эффективность и Удобство
*   **Умная сортировка:** Единый файл теперь автоматически именуется как `_(ИмяПроекта)_Full_Source_code.txt`. Символ `_` в начале гарантирует, что файл всегда будет первым в списке проводника.
*   **Современный UI:** Темный интерфейс (High Contrast Dark Theme) в стиле современных IDE (VS Code / JetBrains).
*   **Обратная связь:** Встроенный **Progress Bar** и статусная строка позволяют отслеживать процесс обработки больших проектов в реальном времени.

### 🛡️ Безопасность
*   **Non-Destructive:** Приложение **никогда** не меняет исходные файлы. Все результаты сохраняются в отдельную папку `_ConvertedToTxt` внутри вашего проекта.
*   **Игнорирование мусора:** Встроенные пресеты автоматически исключают системные папки (`.git`, `node_modules`, `Library`, `target`, `.godot`), чтобы в отчет попадал только чистый код.

### ⚙️ Гибкая настройка
*   **Пресеты:** Готовые настройки для **Unity**, **Godot**, **Java (Maven/Gradle)**, **Web Frontend**.
*   **Древовидный выбор:** Удобное меню выбора файлов с группировкой по расширениям.
*   **Структура проекта:** Опциональная генерация файла `_FileStructure.md`, который рисует дерево папок вашего проекта.

---

## 🚀 Как использовать

1.  Запустите `TxtConverter.exe`.
2.  (Только в первый раз) Выберите язык интерфейса.
3.  Нажмите **"Выбрать..."** и укажите корневую папку вашего проекта.
4.  Выберите **Пресет** (например, *Unity Engine* или *Godot Engine*).
5.  Нажмите **"Пересканировать"**, чтобы увидеть список найденных файлов.
6.  (Опционально) Нажмите **"Выбрать файлы..."**, чтобы отметить галочками только те скрипты, которые нужны вам в полном объеме. Остальные файлы будут добавлены в отчет как заглушки.
7.  Убедитесь, что галочка **"Создавать единый файл..."** включена.
8.  Нажмите большую синюю кнопку **"НАЧАТЬ КОНВЕРТАЦИЮ"**.
9.  По завершении зайдите в появившуюся папку `_ConvertedToTxt` и заберите готовый файл.

---

## 💻 Технологический стек

*   **Язык:** Java 21
*   **UI Framework:** JavaFX 21 (FXML + CSS Styling)
*   **Concurrency:** JavaFX `Task<V>` API
*   **Build System:** Maven
    *   `jpackage-maven-plugin`: Создание самодостаточного установщика/экзешника (JRE включена внутрь).

---

*TxtConverter — Making AI coding easier.*