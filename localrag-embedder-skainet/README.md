# localrag-embedder-skainet

A Kotlin Multiplatform embedding engine for LocalRAG, built on
[SKaiNET](https://github.com/SKaiNET-developers/SKaiNET) tensors. One `commonMain`
implementation compiles for the JVM (where it replaces the Python build-time sidecar) and for
`iosArm64` / `iosSimulatorArm64` (where no LiteRT-LM embedding path exists at all).

## Why this module exists

The embedding design in `tools/embed/README.md` names the risk precisely: build-time and
runtime embeddings are two different programs, and "a different embedding stack at build time
would produce vectors describing a different space from the queries the phone generates, and
nothing would error — retrieval would just get quietly worse." Holding that line today costs:

- a Python venv and `litert-lm-api` wheel on every machine that builds with embedding enabled,
- LiteRT-LM versions pinned twice (`gradle/libs.versions.toml` and `tools/embed/requirements.txt`),
- the `EmbeddingParity` manifest contract policing what the twin stacks might disagree on,
- and an iOS story that cannot exist, because the runtime half is LiteRT-LM on Android.

This module attacks the root cause instead of the symptoms: when the sidecar and the phone run
the **same compiled Kotlin**, parity is true by construction. The manifest contract stays — it
is still the right defence against a stale bundle meeting a newer embedder — but it stops being
the only thing between a toolchain drift and silently worse retrieval.

## What is implemented

`SkaiNetHashedProjectionEmbedder` — a deterministic hashed random projection: signed term
frequencies projected through per-token pseudo-random rows (FNV-1a → splitmix64, ±1/√D), with
the projection running as a SKaiNET `matmul` on `DirectCpuExecutionContext`. Unlike
`hash_embed.py`'s pseudo-vectors, cosine similarity in this space tracks token overlap, so it
exercises the full hybrid retrieval path (vectors + RRF against BM25) with measurable quality —
no model download, no licence, no network.

It is intentionally not a neural model. It is the smallest embedder that makes hybrid retrieval
*testable*, sitting behind the same seam a real model slots into: SKaiNET loads GGUF /
SafeTensors / ONNX weights, so an EmbeddingGemma-class encoder is a model file plus a forward
pass in this same `commonMain` — not a new toolchain.

## Build-time use (replaces the Python sidecar)

```bash
./gradlew -p localrag-embedder-skainet sidecarJar
./gradlew :app:generateLocalRagBundle -PlocalRagEmbedSkainet=true
```

`tools/skainet-embed/skainet_embed.sh` speaks the exact sidecar protocol from
`tools/embed/README.md` (JSON object per line in, JSON float array per line out), so
`EmbedChunksTask` and the plugin are untouched. Requires a Java 21 runtime (SKaiNET 0.57.0
publishes Java 21 bytecode); the wrapper enables the JDK Vector API so SKaiNET picks SIMD ops
where the JVM offers them.

## Runtime use (the iOS door)

The module publishes `iosArm64` and `iosSimulatorArm64` artifacts, and `commonTest` runs the
same suite on the iOS simulator as on the JVM — including a golden-vector test pinning the
embedding space across platforms. An Android/iOS runtime adapter is deliberately left out of
this change to keep it reviewable; on Android it is ~20 lines implementing the internal
`Embedder` seam next to `QueryEmbedder`, declaring `modelId = "skainet-hrp-v1"`, empty
prefixes, 256 dimensions, normalized vectors.

## Tests

```bash
./gradlew -p localrag-embedder-skainet jvmTest
./gradlew -p localrag-embedder-skainet iosSimulatorArm64Test
```

Covered: dimensions and unit norm, determinism, cosine tracking token overlap (related text
must clearly outscore unrelated), zero-vector behaviour for token-free text, and cross-platform
golden parity.
