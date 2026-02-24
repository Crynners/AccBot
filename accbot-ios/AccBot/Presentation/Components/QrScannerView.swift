import SwiftUI
import AVFoundation

/// AVFoundation-based QR code scanner wrapped in UIViewRepresentable.
/// Returns the scanned string via the `onCodeScanned` closure.
struct QrScannerView: UIViewRepresentable {
    let onCodeScanned: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCodeScanned: onCodeScanned)
    }

    func makeUIView(context: Context) -> UIView {
        let containerView = UIView(frame: .zero)
        containerView.backgroundColor = .black

        guard let device = AVCaptureDevice.default(for: .video) else {
            addErrorLabel(to: containerView, text: "Camera not available")
            return containerView
        }

        guard let input = try? AVCaptureDeviceInput(device: device) else {
            addErrorLabel(to: containerView, text: "Cannot access camera")
            return containerView
        }

        let session = AVCaptureSession()
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        session.addOutput(output)
        output.setMetadataObjectsDelegate(context.coordinator, queue: .main)
        output.metadataObjectTypes = [.qr]

        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        containerView.layer.addSublayer(previewLayer)
        context.coordinator.previewLayer = previewLayer

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
        context.coordinator.session = session

        return containerView
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.previewLayer?.frame = uiView.bounds
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.session?.stopRunning()
    }

    private func addErrorLabel(to view: UIView, text: String) {
        let label = UILabel()
        label.text = text
        label.textColor = .white
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    // MARK: - Coordinator

    class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let onCodeScanned: (String) -> Void
        var session: AVCaptureSession?
        var previewLayer: AVCaptureVideoPreviewLayer?
        private var hasScanned = false

        init(onCodeScanned: @escaping (String) -> Void) {
            self.onCodeScanned = onCodeScanned
        }

        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard !hasScanned,
                  let metadata = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let value = metadata.stringValue
            else { return }

            hasScanned = true
            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            session?.stopRunning()
            onCodeScanned(value)
        }

        /// Allow rescanning (e.g. when the sheet is re-presented).
        func reset() {
            hasScanned = false
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.session?.startRunning()
            }
        }
    }
}

/// Convenience wrapper that presents the scanner in a sheet with an
/// overlay frame and a cancel button.
struct QrScannerSheet: View {
    let title: String
    let onScanned: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                QrScannerView { code in
                    onScanned(code)
                    dismiss()
                }
                .ignoresSafeArea()

                // Scanning frame overlay
                RoundedRectangle(cornerRadius: CornerRadius.lg)
                    .strokeBorder(colors.primary, lineWidth: 2)
                    .frame(width: 250, height: 250)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(String(localized: "Cancel")) { dismiss() }
                        .foregroundColor(colors.primary)
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    QrScannerSheet(
        title: "Scan Wallet QR",
        onScanned: { _ in }
    )
    .preferredColorScheme(.dark)
}
