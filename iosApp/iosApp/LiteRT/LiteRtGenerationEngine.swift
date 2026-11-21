import Foundation
import LiteRTLM
import Shared
import os

/// A loaded LiteRT-LM generation model, implementing the shared SDK's generation seam.
///
/// Every generate decodes on a fresh conversation that is released before returning, so no
/// turn can leak into the next answer. Failures return nil so the caller falls back rather
/// than erroring. The Kotlin side owns the policy (single flight, memory-warning unload);
/// this class only keeps generate, cancelGeneration and close safe to call from any thread.
final class LiteRtGenerationEngine: NSObject, GenerationEngine {
    private static let logger = Logger(
        subsystem: "com.ayushig.localrag",
        category: "LocalRagEngine"
    )

    /// Guards currentTask, activeConversation, latestGenerationID and closed. Never held
    /// across an await or a native call; cancel/close copy the references and act outside.
    private let stateLock = NSLock()
    private var engine: Engine?
    private var currentTask: Task<Void, Never>?
    private var activeConversation: Conversation?
    private var latestGenerationID = 0
    private var closed = false

    init(engine: Engine) {
        self.engine = engine
    }

    // MARK: - GenerationEngine

    func generate(
        request: GenerationRequest,
        completionHandler: @escaping (String?, Error?) -> Void
    ) {
        let generationID: Int? = stateLock.withLock {
            if closed {
                return nil
            }
            latestGenerationID += 1
            return latestGenerationID
        }
        guard let generationID else {
            completionHandler(nil, nil)
            return
        }

        let task: Task<Void, Never> = Task {
            let output = await self.runGenerate(request: request, generationID: generationID)
            self.stateLock.withLock {
                if self.latestGenerationID == generationID {
                    self.currentTask = nil
                    self.activeConversation = nil
                }
            }
            completionHandler(output, nil)
        }

        let registered: Bool = stateLock.withLock {
            if generationID == latestGenerationID, !closed {
                currentTask = task
                return true
            }
            return false
        }
        if !registered {
            // A newer generate or close() won the race before registration: stop this one;
            // its body observes the cancellation and reports nil exactly once.
            task.cancel()
        }
    }

    func cancelGeneration() {
        let (task, conversation): (Task<Void, Never>?, Conversation?) = stateLock.withLock {
            (currentTask, activeConversation)
        }
        // The Task flag makes the generate loop return nil; conversation.cancel() ends the
        // native stream within a millisecond so the return is prompt.
        task?.cancel()
        if let conversation {
            try? conversation.cancel()
        }
    }

    func close() {
        let (task, conversation): (Task<Void, Never>?, Conversation?) = stateLock.withLock {
            if closed {
                return (nil, nil)
            }
            closed = true
            let task = currentTask
            let active = activeConversation
            currentTask = nil
            activeConversation = nil
            // Releasing the engine reference frees the native memory once any in-flight
            // generate (holding its own local reference) finishes.
            engine = nil
            return (task, active)
        }
        task?.cancel()
        if let conversation {
            try? conversation.cancel()
        }
    }

    // MARK: - Generation

    private func runGenerate(request: GenerationRequest, generationID: Int) async -> String? {
        if Task.isCancelled {
            return nil
        }
        let liveEngine: Engine? = stateLock.withLock {
            closed ? nil : engine
        }
        guard let liveEngine else {
            return nil
        }

        do {
            // Greedy, as on Android: the answer must be reproducible, and creativity has no
            // value when the model is only allowed to restate a passage.
            let sampler = try SamplerConfig(topK: 1, topP: 1.0, temperature: 0.0)
            let conversation = try await liveEngine.createConversation(
                with: ConversationConfig(
                    systemMessage: Message(request.systemInstruction),
                    samplerConfig: sampler
                )
            )
            if Task.isCancelled {
                return nil
            }
            let keepGoing: Bool = stateLock.withLock {
                if closed || Task.isCancelled {
                    return false
                }
                if generationID == latestGenerationID {
                    activeConversation = conversation
                }
                return true
            }
            if !keepGoing {
                return nil
            }

            var output = ""
            let maxOutputTokens = Int(request.maxOutputTokens)
            for try await chunk in conversation.sendMessageStream(
                Message(request.prompt),
                maxOutputTokens: maxOutputTokens
            ) {
                if Task.isCancelled {
                    return nil
                }
                output += chunk.toString
                // Backstop in case the native max-output limit is ignored: about six
                // characters per token is generous headroom over plain text.
                if output.count > maxOutputTokens * 6 {
                    try? conversation.cancel()
                    break
                }
            }
            if Task.isCancelled {
                return nil
            }
            return output
        } catch {
            Self.logger.warning("LiteRT-LM generation failed")
            return nil
        }
    }
}
