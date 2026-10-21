# Audio to WAV

Convert audio and video files to WAV — from the desktop with a Python script, or on Android with a Jetpack Compose app. Both use FFmpeg under the hood.

## Project structure

```
.
├── audio_to_wav.py     # Desktop converter (CLI + simple GUI pickers)
└── android_app/        # Android app (Kotlin, Jetpack Compose, FFmpeg-Kit)
```

## Desktop converter (`audio_to_wav.py`)

### Requirements

- Python 3.10+
- [FFmpeg](https://ffmpeg.org/download.html) available on your `PATH`
- Tkinter (bundled with most Python installs) for the file/folder pickers

### Supported input formats

`mp3, wav, ogg, flac, m4a, aac, wma, opus, webm, mp4, mkv, avi, mov, flv`

### Usage

Run without arguments to choose files or a folder through a dialog:

```bash
python audio_to_wav.py
```

Or pass files and/or folders directly:

```bash
python audio_to_wav.py song.mp3
python audio_to_wav.py ./music -o ./converted --sample-rate 44100 --channels 2
```

| Option | Description |
| --- | --- |
| `input` | One or more files or directories. Opens a picker if omitted. |
| `-o, --output` | Output file, or output directory when converting several files. |
| `--sample-rate` | Output sample rate, e.g. `44100`. |
| `--channels` | `1` (mono) or `2` (stereo). |
| `-t, --threads` | Parallel conversions. Defaults to the number of CPU cores. |

By default, converted files are written to an `output/` folder next to the source files. If that folder already has content, you'll be asked whether to clean it first.

## Android app (`android_app/`)

A Jetpack Compose app that lets you pick multiple audio files, set the sample rate and channel count, and batch-convert them to WAV with [FFmpeg-Kit](https://github.com/arthenica/ffmpeg-kit). Converted files are saved to `Music/AudioToWav` on the device.

### Requirements

- Android Studio (or JDK 17 + Android SDK 34)
- Min SDK 24, target SDK 34

### Build

Open `android_app/` in Android Studio, or from the command line:

```bash
cd android_app
./gradlew assembleDebug
```

The debug APK is written to `android_app/app/build/outputs/apk/debug/`.

## License

This project is provided as-is for personal and educational use.
