### 1. Architecture: The "Markdown in Assets" Pattern

Instead of hardcoding text into strings or data classes, you will treat the `assets` folder as your local database.

*   **Storage:** Store the modified Markdown (`.md`) files in `src/main/assets/guide/`.
*   **Structure:** Maintain the language-based folder structure (`assets/guide/en/`, `assets/guide/fr/`, etc.) to support future localization easily.
*   **Manifest:** Create a simple JSON file (e.g., `toc.json`) in each language folder to define the **order** and **titles** of the guide sections. This prevents you from having to parse filenames or rely on file system sorting.

### 2. Content Workflow (The "Fork & Adapt" Strategy)

You cannot use the Readeck files verbatim because they describe a Web UI.

1.  **Ingest:** Copy the relevant English `.md` files from the Readeck repo into `src/main/assets/guide/en/`.
    *   *Keep:* `saving.md`, `reading.md`, `organizing.md` (or similar).
    *   *Discard:* `installation.md`, `deployment.md`, `administration.md`.
2.  **Adapt (The Writing Phase):** Open these files in your IDE and edit them:
    *   **Terminology:** Change "Click" to "Tap".
    *   **UI References:** Change "Sidebar" to "Navigation Drawer" or "Bottom Sheet".
    *   **Shorten:** Mobile users read less. Break large paragraphs into bullet points.
3.  **Images:** The Markdown files likely link to relative images (`![Screenshot](../images/foo.png)`).
    *   Create a parallel `images` folder in assets.
    *   Replace the web screenshots with **mobile screenshots** you take from your emulator.
    *   Update the paths in the Markdown to point to your new assets.

### 3. Functional Design (UI/UX)

This should follow the **Material Design 3** (Material You) guidelines strictly.

#### Screen 1: The Guide Index (Table of Contents)
*   **Layout:** A standard `LazyColumn`.
*   **Components:** `ListItem` composables.
    *   **Headline:** The section title (from your `toc.json`).
    *   **Leading Content:** An icon representing the topic (e.g., a "Bookmark" icon for Saving, "Glasses" for Reading).
    *   **Trailing Content:** A standardized chevron `>`.
*   **Behavior:** Tapping an item navigates to the *Guide Detail* route, passing the filename as an argument.

#### Screen 2: The Guide Detail (The Reader)
*   **Top App Bar:**
    *   Title: The Section Name.
    *   Navigation: Back button.
*   **Content Area:**
    *   This is where you use a **Compose Markdown Renderer**.
    *   **Crucial for MD3:** Do not use a library's default styling. You must configure the renderer to map Markdown elements to your specific Material Theme typography:
        *   `# Header 1` -> `MaterialTheme.typography.headlineMedium`
        *   `## Header 2` -> `MaterialTheme.typography.titleLarge`
        *   `Paragraph` -> `MaterialTheme.typography.bodyLarge`
    *   **Colors:** Ensure the text color uses `MaterialTheme.colorScheme.onSurface` so it automatically adapts to Dark Mode.

### 4. Technical Implementation Plan

#### Step 1: Dependencies
Add a library that renders Markdown to Compose UI.
*   *Recommendation:* **`com.github.jeziellago:compose-markdown`** or **`com.mikepenz:multiplatform-markdown-renderer`**.
*   These libraries allow you to override the styling of specific elements (Headers, Links, etc.) to ensure it looks like a native part of your app, not a web page.

#### Step 2: Data Layer (Repository)
Create a `UserGuideRepository` class.
*   **Function:** `getGuideStructure(language: String): List<GuideSection>`
    *   Parses the `toc.json` from assets.
*   **Function:** `getGuideContent(filename: String): String`
    *   Reads the raw string content of the `.md` file from assets.
*   **Localization Logic:**
    *   Check `Locale.getDefault().language`.
    *   If a folder exists for that language in assets, use it.
    *   Else, fallback to `en`.

#### Step 3: Image Handling
This is the trickiest part of Markdown in Android.
*   The Markdown library will encounter `![Alt](filename.png)`.
*   You usually need to provide an `ImageLoader` or `Coil` integration that knows how to load `file:///android_asset/guide/images/filename.png`.

### 5. Summary of Work
1.  **Copy** selected `.md` files to `assets/guide/en`.
2.  **Create** `assets/guide/en/toc.json` to list them.
3.  **Edit** the text in the `.md` files to match your App's UI.
4.  **Create** `UserGuideRepository` to read these files.
5.  **Build** the Compose screens using a Markdown library, applying your `MaterialTheme` typography to the configuration.

This plan gives you the "Single Source of Truth" structure of the original Readeck guide but gives you full control over the mobile-specific presentation and content adaptation.