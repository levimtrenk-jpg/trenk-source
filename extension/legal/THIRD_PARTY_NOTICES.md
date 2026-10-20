# Third-Party Notices – TRENK / Universal Downloader

TRENK (Universal Downloader) is built on the open-source components listed below. Each component remains under its own license; the full license texts are included in the `legal/licenses` folder (in the app: Settings → Licenses & credits; in the extension: popup → Licenses & credits).

TRENK itself is free software, licensed under the GNU General Public License v3.0 (see `GPL-3.0.txt`). Source code: https://github.com/levimtrenk-jpg/trenk-source

TRENK is not affiliated with YouTube, Google, TikTok, ByteDance, Instagram or Meta.

## Android app

### yt-dlp
Video/audio extraction engine. Downloaded and updated automatically at runtime from the official yt-dlp releases.
License: The Unlicense (public domain). See `Unlicense-yt-dlp.txt`.
Source: https://github.com/yt-dlp/yt-dlp

### youtubedl-android (JunkFood02 fork of yausername/youtubedl-android)
Android wrapper that bundles yt-dlp, Python and FFmpeg (library version 0.18.1). Includes code from youtubedl-java by sapher.
License: GNU General Public License v3.0. See `GPL-3.0.txt`.
Source: https://github.com/JunkFood02/youtubedl-android (original: https://github.com/yausername/youtubedl-android)

### FFmpeg 7.1.1
Merging and converting audio/video. The binaries shipped inside youtubedl-android 0.18.1 are the Termux build of FFmpeg 7.1.1, configured with GPL and version-3 components, so this build as a whole is licensed under the **GNU General Public License version 3 or later**. Parts of FFmpeg itself are LGPL 2.1+.
License: GPL-3.0-or-later (see `GPL-3.0.txt`); FFmpeg's own LGPL parts: LGPL-2.1-or-later (see `LGPL-2.1.txt`).
Source code of the exact version used: https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz
Build recipe (configure flags and patches): https://github.com/termux/termux-packages/tree/master/packages/ffmpeg
FFmpeg is a trademark of Fabrice Bellard, originator of the FFmpeg project. https://ffmpeg.org

### Libraries bundled with that FFmpeg build
The Termux FFmpeg package also ships these shared libraries, each under its own license (source and license files for each are available from the Termux packages repository, https://github.com/termux/termux-packages/tree/master/packages): libx264 and libx265 (GPL-2.0-or-later), xvidcore (GPL-2.0-or-later), libvidstab (GPL-2.0-or-later), rubberband (GPL-2.0-or-later), libmp3lame (LGPL-2.0-or-later), libmpg123 (LGPL-2.1), gnutls (LGPL-2.1-or-later), nettle/hogweed (LGPL-3.0-or-later / GPL-2.0-or-later), gmp (LGPL-3.0-or-later / GPL-2.0-or-later), libass (ISC), libaom, dav1d, rav1e, SVT-AV1, libvpx, libwebp, opus, libogg, libvorbis, libtheora, libsrt (MPL-2.0), libssh (LGPL-2.1), libbluray (LGPL-2.1), libopenmpt (BSD-3-Clause), libgme (LGPL-2.1), libsoxr (LGPL-2.1), libsamplerate (BSD-2-Clause), fftw3 (GPL-2.0-or-later), opencore-amr and vo-amrwbenc (Apache-2.0), zimg (WTFPL), libvmaf (BSD-2-Clause-Patent), libzmq (MPL-2.0), libsodium (ISC), freetype (FTL), fontconfig, fribidi (LGPL-2.1), harfbuzz, graphite2, glib (LGPL-2.1-or-later), libxml2, libiconv (LGPL-2.1), libidn2 and libunistring (LGPL-3.0-or-later / GPL-2.0-or-later), pcre2, lcms2, libpng, zlib, bzip2, xz/liblzma, brotli, libffi (MIT) and v4l-utils (LGPL-2.1).

### Python
Runtime for yt-dlp, bundled inside youtubedl-android.
License: Python Software Foundation License Version 2 (plus the licenses of Python's bundled modules, listed in the same file). See `PSF-Python.txt`.
Source: https://www.python.org/downloads/source/

### QuickJS
JavaScript engine used by yt-dlp for YouTube, bundled inside youtubedl-android.
License: MIT. Copyright (c) 2017-2021 Fabrice Bellard and Charlie Gordon. See `MIT-QuickJS.txt`.
Source: https://bellard.org/quickjs/

### Rubik font
The app's typeface.
License: SIL Open Font License 1.1. Copyright 2015 The Rubik Project Authors. See `OFL-1.1-Rubik.txt`.
Source: https://github.com/googlefonts/rubik

### Sentry SDK for Android
Crash and error reports (no personal data is sent – see the privacy policy).
License: MIT. Copyright (c) 2019 Sentry. See `MIT-Sentry.txt`.
Source: https://github.com/getsentry/sentry-java

### Android Jetpack, Kotlin and Coil
AndroidX (Core, Activity, Lifecycle, WorkManager, Jetpack Compose, Material 3, Material Icons), Kotlin standard library, kotlinx.coroutines and Coil (image loading).
License: Apache License 2.0. See `Apache-2.0.txt`.
Sources: https://android.googlesource.com/platform/frameworks/support · https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines · https://github.com/coil-kt/coil

## Windows engine and Chrome extension

The Windows package contains only TRENK's own code. The installer downloads the following components from their official sources onto the user's computer; they are not redistributed inside the package.

### yt-dlp (with curl_cffi)
License: The Unlicense (yt-dlp, see `Unlicense-yt-dlp.txt`); curl_cffi: MIT (see `MIT-curl_cffi.txt`).
Sources: https://github.com/yt-dlp/yt-dlp · https://github.com/lexiforest/curl_cffi

### Python 3.13 (installed with winget)
License: Python Software Foundation License Version 2. See `PSF-Python.txt`.

### FFmpeg (Gyan.dev "full" build, installed with winget)
License: GPL-3.0-or-later. See `GPL-3.0.txt`.
Source: https://www.gyan.dev/ffmpeg/builds/ · https://ffmpeg.org/download.html

### FastAPI and Uvicorn
Local web server of the engine.
Licenses: FastAPI – MIT (see `MIT-FastAPI.txt`); Uvicorn – BSD-3-Clause (see `BSD-3-Uvicorn.txt`).
Sources: https://github.com/fastapi/fastapi · https://github.com/encode/uvicorn

### pystray and Pillow
Tray icon.
Licenses: pystray – LGPL-3.0 (see `LGPL-3.0.txt`); Pillow – MIT-CMU/HPND (see `HPND-Pillow.txt`).
Sources: https://github.com/moses-palmer/pystray · https://github.com/python-pillow/Pillow

## Website

The download page uses GoatCounter (https://www.goatcounter.com) for cookie-free anonymous counts and ntfy (https://ntfy.sh) for anonymous download notifications. These are external services; no code from them is distributed in the app.

## Contact

Questions or license requests: see the contact address on https://levimtrenk-jpg.github.io/trenk/terms.html
