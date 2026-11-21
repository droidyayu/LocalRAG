import Foundation
import LiteRTLM
import Shared
import os

/// Loads the LiteRT-LM generation engine onto one compute backend.
///
/// Thin Swift wrapper: the load policy (GPU first, "reply OK" probe, CPU fallback, one
/// engine per process) lives in the shared Kotlin SDK. Load failures are reported through
/// the completion handler; the Kotlin policy decides the fallback and the message.
final class LiteRtGenerationBackend: NSObject, GenerationBackend {
    private static let logger = Logger(
        subsystem: "com.ayushig.localrag",
        category: "LocalRagEngine"
    )

    func load(
        modelPath: String,
        cacheDir: String,
        compute: ComputeBackend,
        completionHandler: @escaping (GenerationEngine?, Error?) -> Void
    ) {
        // Kotlin enum entries are singletons, so identity comparison is exact.
        let backendName = compute === ComputeBackend.gpu ? "gpu" : "cpu"
        Self.logger.info(
            "Loading LiteRT-LM generation engine (backend: \(backendName, privacy: .public))"
        )
        Task {
            do {
                let backend: Backend = compute === ComputeBackend.gpu ? .gpu : .cpu()
                // Never pass maxNumTokens: the spike proved a small value breaks tensor
                // allocation ("DYNAMIC_UPDATE_SLICE"); nil keeps the model default.
                let config = try EngineConfig(modelPath: modelPath, backend: backend, cacheDir: cacheDir)
                let engine = Engine(engineConfig: config)
                try await engine.initialize()
                completionHandler(LiteRtGenerationEngine(engine: engine), nil)
            } catch {
                Self.logger.warning("LiteRT-LM generation load failed")
                completionHandler(nil, error)
            }
        }
    }
}
