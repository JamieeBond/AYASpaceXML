<div align="center">
<img src=".github/resources/icon.png" height="180px" width="auto" alt="romm logo">
<br>
![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![Language](https://img.shields.io/badge/language-Kotlin-purple.svg)
</div>

# AYASpaceXML

Copy ES-DE 'gamelist.xml' files and media assets ('image', 'thumbnail') to enable full AYASpace compatibility.

### Directory Structure

**Source (ES-DE):**
```
ES-DE/
├── gamelists/
│   └── n3ds/
│       └── gamelist.xml
└── downloaded_media/
    └── n3ds/
        ├── covers/
        ├── fanart/
        └── screenshots/
```

**Destination (ROMs for AYASpace):**
```
ROMs/
└── n3ds/
    ├── gamelist.xml
    └── media/
        ├── image/        (fanart or screenshots)
        └── thumbnail/    (covers)
```

### Media Processing Logic

| Source | Destination | Fallback |
|--------|-------------|----------|
| **Covers** → `thumbnail/` | Box art for game lists | None |
| **Fanart** → `image/` | Full-screen artwork | Falls back to screenshots if no fanart |
| **Screenshots** → `image/` | Used only if no fanart exists | None |

### Usage

1. **Select ES-DE Folder** - Point to your ES-DE installation directory
2. **Select ROMs Folder** - Point to your AYASpace ROMs directory
3. **Press Copy**

Each time you run the copy operation:
- Old media files are automatically cleaned up
- Fresh copies are made from ES-DE
- Gamelist XML is regenerated with correct paths
