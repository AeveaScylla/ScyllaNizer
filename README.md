<img width="3446" height="1267" alt="1824" src="https://github.com/user-attachments/assets/995e66e2-8a9d-413f-a585-632cc4a81ffb" />
<img width="3495" height="1947" alt="4283" src="https://github.com/user-attachments/assets/d84583ef-f7c4-4894-bf90-1247f24f88f5" />
# 🐉 ScyllaNizer

**ScyllaNizer** is a sleek, powerful, and ultra-fast file organizer for Android. It scans any folder (including `Download` and root storage), categorizes files by extension, and safely moves them—with a full dry-run preview and instant undo.

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

