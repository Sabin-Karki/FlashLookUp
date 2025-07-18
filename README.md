# WordLookUp

Copy a word. Get its definition instantly.

## What it does

WordLookUp sits in your system tray and watches your clipboard. When you copy a single word, it shows you a quick popup with the definition and lets you hear how it's pronounced. That's it.

## Quick start

1. Download the latest release
2. Extract everything to a folder
3. Run: `java -cp "./*" WordLookUp`
4. Copy any word and watch the magic happen

## Building it yourself

Need Java 8+ and these JAR files (download links in the original project):
- jlayer-1.0.1.jar
- basicplayer-3.0.0.0.jar  
- mp3spi-1.9.5-1.jar
- tritonus-share-0.3.6.jar

```bash
# Put all JARs in the same folder, then:
javac -cp "./*" WordLookUp.java
java -cp "./*" WordLookUp
```

## Features

- Works on Windows, Mac, and Linux
- Only triggers on single words (ignores sentences)
- Audio pronunciation with a click
- Popup appears near your cursor
- Auto-disappears after 6 seconds
- Uses the free Dictionary API (no signup needed)

## Common problems

**No popup showing?** Make sure you're copying single words only. Check the terminal for errors.

**No sound?** Some words don't have audio files. Also check your internet connection.


## Want to help?

Fork it, make it better, send a pull request. The code is pretty straightforward.


*Uses the free Dictionary API and some Java audio libraries. No data collection, everything happens locally.*
