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

Python 3.14 is supported: torch 2.14.0 and onnxruntime 1.30.0 both publish cp314 macOS arm64
wheels, and sentence-transformers is pure Python.

```bash
python3 -m venv tools/embed/.venv
tools/embed/.venv/bin/pip install -r tools/embed/requirements.txt
```

The model is gated. Accept the licence at huggingface.co/google/embeddinggemma-300m and
authenticate with `huggingface-cli login` before the first run.

Then point the plugin at it:

```kotlin
localRag {
    embedding {
        enabled.set(true)
        dimensions.set(256)
        sidecarCommand.set(
            listOf(
                rootProject.file("tools/embed/.venv/bin/python").absolutePath,
                rootProject.file("tools/embed/embed.py").absolutePath,
                "--dimensions", "256",
            ),
        )
    }
}
```

## Why the prefixes matter

EmbeddingGemma expects a task prefix on queries and documents. The plugin writes whichever prefixes
it used into the bundle manifest, and the runtime refuses to use vectors whose prefixes, model id
or dimension count differ from its own configuration. A prefix mismatch does not error on its own —
it quietly ruins retrieval — so that check is the only thing standing between a typo and a silently
worse assistant.
