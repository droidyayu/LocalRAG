import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Generation and embedding backends come from the Swift LiteRT-LM engine files (added
        // next); nil keeps the app retrieval-only, which is a supported state.
        _ = KoinIosKt.doInitKoinIos(
            litertlmVersion: "0.17.0",
            appVersion: "4.5",
            generationBackend: nil,
            embeddingBackend: nil
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
        }
    }
}
