import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Generation runs on the Swift LiteRT-LM engine; the embedding backend stays nil
        // (D4: iOS retrieval is BM25-only for now).
        _ = KoinIosKt.doInitKoinIos(
            litertlmVersion: "0.17.0",
            appVersion: "4.5",
            generationBackend: LiteRtGenerationBackend(),
            embeddingBackend: nil
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
        }
    }
}
