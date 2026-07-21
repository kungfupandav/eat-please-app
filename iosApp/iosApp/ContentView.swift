import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Go edge-to-edge so the cream Compose background fills behind the
            // status bar and home indicator; Compose applies safe-area insets to
            // its own content (Scaffold safeDrawing) and handles the keyboard.
            .ignoresSafeArea()
    }
}
