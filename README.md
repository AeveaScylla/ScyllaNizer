<img width="3460" height="1953" alt="5595" src="https://github.com/user-attachments/assets/41db7dbb-ede3-4cb5-8a21-6a208dc00517" />

<img width="3495" height="1947" alt="4283" src="https://github.com/user-attachments/assets/d84583ef-f7c4-4894-bf90-1247f24f88f5" />
# 🐉 ScyllaNizer

**ScyllaNizer** is a sleek, powerful, and ultra-fast file organizer for Android. It scans any folder (including `Download` and root storage), categorizes files by extension, and safely moves them around without ever overwriting your data.

Built with **Kotlin** and **Jetpack Compose**, ScyllaNizer features a gorgeous "Glossy Black & Electric Cyan" UI that adapts perfectly to both phones and large tablets (like the 13" Idea Tab Pro).

## ✨ Features

- **Full Storage Access:** Bypasses Android Scoped Storage (using `MANAGE_EXTERNAL_STORAGE`) to organize `Download`, `DCIM`, or any folder.
- **Dry-Run Preview:** See exactly what will move, where it will go, and what will be renamed *before* a single file touches the disk.
- **Smart Sorting:** Automatically categorizes files into Images, Videos, Audio, Documents, Archives, Apps, Fonts, Code, Design, and Others.
- **Customizable Structure:** Optionally sort into per-extension (`Images/JPG/`) or date-based (`Images/2026-10/`) subfolders.
- **Folder Exclusions:** Multi-select checkboxes to skip specific folders (like `Telegram` or `WhatsApp`).
- **Duplicate Protection:** Auto-renames conflicts (`photo.jpg` → `photo (1).jpg`) without ever overwriting.
- **Instant Undo:** Revert the last sort operation instantly if you change your mind.
- **Shredder:** Securely overwrite and delete rejected files with random data.
- **Wi-Fi Drop (HTTP Server):** Turn on the built-in local web server and drag-and-drop files from your PC browser directly to your phone over your local Wi-Fi network.
- **Adaptive UI:** A beautiful, responsive two-column layout on tablets, and a smooth single-column layout on phones.

## 🤔 How does ScyllaNizer compare?

ScyllaNizer is not the only project of its kind, but its exact combination of glossy aesthetics, a built-in PC drag-and-drop server, and dry-run previews makes it quite unique. Several other open-source (FOSS) and proprietary tools on Android focus specifically on automated file organization and smart categorization, but they all take different approaches.

### Direct Open-Source Alternatives
*   **FilePipe:** Perhaps the closest direct open-source competitor. It is built specifically around automated sorting using smart rules. You can set it to automatically move, copy, or sort media and files based on extension, size, or custom name patterns on a daily or weekly schedule.
*   **AnExplorer:** An open-source file explorer that features automatic sorting across various device types (phones, tablets, and Android TVs).
*   **Pile:** A privacy-first, offline FOSS application designed specifically to organize clutter and loose documents using tags instead of rigid folder structures.

### The AI & Advanced Categorization Shift
If you look slightly outside the strict FOSS space into the broader Android ecosystem, automation has moved heavily toward AI-driven sorting:
*   **Filex AI:** Unlike extension-based sorting, this tool reads file content (like PDFs, images, and text) to generate logical structures and rename files automatically using plain English workflows.
*   **Cx File Explorer:** While a traditional manager, its integrated Storage Analyzer is frequently used to instantly categorize heavy storage users by file type, duplicate files, and large clutter blocks.

### Why ScyllaNizer Stands Out
ScyllaNizer stands out because many traditional FOSS mainstays—like Amaze File Manager or Fossify—excel at traditional file workflows and privacy, but they don't automatically restructure a messy directory out-of-the-box like a dedicated organizer does.

ScyllaNizer bridges the gap:
1. **No complex rules to set up** (unlike FilePipe). Just pick a folder and tap Scan.
2. **100% Offline & Private** (no cloud AI reading your files).
3. **The Wi‑Fi Drop feature** (no other organizer has a built-in local HTTP server to receive files from a PC).
4. **Full Dry-Run Preview** so you have absolute control over what gets moved before a single file touches the disk.
