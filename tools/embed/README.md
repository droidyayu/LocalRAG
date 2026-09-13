# LocalRAG embedding sidecar

The Gradle plugin shells out to a script here to turn documentation chunks into vectors. Shelling
out rather than embedding in the Gradle JVM keeps the model runtime out of the build classpath, at
the cost of a toolchain requirement on every machine that builds with embedding enabled.

## Protocol

One JSON object per line on stdin, one JSON array of floats per line on stdout, same order:

```
{"text": "What is a GTT order? — What it does\n\nA GTT order stays pending..."}
```

```
[0.0123, -0.0456, ...]
```

Any non-zero exit fails the build with whatever the script wrote to stderr.

## Two scripts

`hash_embed.py` produces deterministic pseudo-vectors from a hash. They carry no meaning and are
useless for retrieval quality, but they exercise the whole pipeline without a large download or a
licence, which is what the plugin tests and the demo app use.

`embed.py` produces real vectors with EmbeddingGemma.

## Real vectors

`embed.py` runs on LiteRT-LM, the same runtime the Android app uses. `litert-lm-api` and
`litertlm-android` ship the same version, expose the same `EmbeddingEngine`, consume the same
model file and apply normalization and output truncation the same way. A different embedding
stack at build time would produce vectors describing a different space from the queries the phone
generates, and nothing would error - retrieval would just get quietly worse.

Python 3.14 is supported: `litert-lm-api` publishes `py3-none` wheels for macOS arm64, Linux
x86_64 and aarch64, Windows and Android, and declares `>=3.10`.

```bash
python3 -m venv tools/embed/.venv
tools/embed/.venv/bin/pip install -r tools/embed/requirements.txt
```

Then supply an EmbeddingGemma model and point the plugin at it:

```kotlin
localRag {
    embedding {
        enabled.set(true)
        dimensions.set(256)
        modelId.set("embeddinggemma-300m-seq256")
        sidecarCommand.set(
            listOf(
                rootProject.file("tools/embed/.venv/bin/python").absolutePath,
                rootProject.file("tools/embed/embed.py").absolutePath,
                "--model", "/absolute/path/to/embeddinggemma-300m-seq256.litertlm",
                "--dimensions", "256",
            ),
        )
    }
}
```

`--dimensions` sets the output size through `EmbeddingOptions`. EmbeddingGemma is Matryoshka
trained, so a shorter vector is a supported trade rather than a truncation we invented, and 256
floats per chunk keeps `vectors.bin` small enough to ship in an APK.

### Open question: model format

`litert-community/embeddinggemma-300m` currently publishes `.tflite` files only, one per sequence
length and accelerator, with no `.litertlm`. The Python wrapper does not validate the extension -
the path goes straight to the native layer - so whether `EmbeddingEngine` accepts a bare `.tflite`
is unverified. Settle it when the model is first downloaded; if it does not, the model needs
converting to `.litertlm` first.

## Why the prefixes matter

EmbeddingGemma expects a task prefix on queries and documents. The plugin writes whichever prefixes
it used into the bundle manifest, and the runtime refuses to use vectors whose prefixes, model id
or dimension count differ from its own configuration. A prefix mismatch does not error on its own —
it quietly ruins retrieval — so that check is the only thing standing between a typo and a silently
worse assistant.
