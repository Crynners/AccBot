import SwiftUI
import AVFoundation
import Vision

// MARK: - Scan Mode

/// Determines whether the scanner operates in QR code or text recognition mode.
enum ScanMode: String, CaseIterable {
    case qr
    case text

    var label: String {
        switch self {
        case .qr: return "QR"
        case .text: return String(localized: "Text")
        }
    }

    var systemImage: String {
        switch self {
        case .qr: return "qrcode"
        case .text: return "text.viewfinder"
        }
    }
}

// MARK: - Clean QR Value

/// Strips cryptocurrency URI scheme prefixes and query parameters from a scanned value.
/// e.g. "bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa?amount=0.1" -> "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
func cleanQrValue(_ raw: String) -> String {
    let prefixes = ["bitcoin:", "ethereum:", "litecoin:", "bitcoincash:", "dogecoin:", "monero:"]
    var value = raw
    for prefix in prefixes {
        if value.lowercased().hasPrefix(prefix) {
            value = String(value.dropFirst(prefix.count))
            break
        }
    }
    // Strip query parameters
    if let queryIndex = value.firstIndex(of: "?") {
        value = String(value[..<queryIndex])
    }
    return value.trimmingCharacters(in: .whitespacesAndNewlines)
}

// MARK: - Camera Preview UIView

/// Custom UIView that keeps the AVCaptureVideoPreviewLayer frame in sync
/// with its own bounds via `layoutSubviews()`. This is critical because
/// `UIViewRepresentable.updateUIView` is only called on SwiftUI state changes,
/// NOT on UIKit layout changes — so on initial sheet presentation (especially
/// on iPad) the preview layer would stay at `.zero` frame = black screen.
class CameraPreviewView: UIView {
    var previewLayer: AVCaptureVideoPreviewLayer? {
        didSet {
            oldValue?.removeFromSuperlayer()
            if let layer = previewLayer {
                self.layer.addSublayer(layer)
                layer.frame = bounds
            }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }
}

// MARK: - QR Scanner View

/// AVFoundation-based QR code scanner wrapped in UIViewRepresentable.
/// Returns the scanned string via the `onCodeScanned` closure.
struct QrScannerView: UIViewRepresentable {
    let onCodeScanned: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCodeScanned: onCodeScanned)
    }

    func makeUIView(context: Context) -> CameraPreviewView {
        let containerView = CameraPreviewView(frame: .zero)
        containerView.backgroundColor = .black
        containerView.accessibilityLabel = NSLocalizedString("Camera preview for QR code scanning", comment: "")
        containerView.isAccessibilityElement = true

        guard let device = AVCaptureDevice.default(for: .video) else {
            addErrorLabel(to: containerView, text: String(localized: "Camera not available"))
            return containerView
        }

        guard let input = try? AVCaptureDeviceInput(device: device) else {
            addErrorLabel(to: containerView, text: String(localized: "Cannot access camera"))
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
        containerView.previewLayer = previewLayer
        context.coordinator.previewLayer = previewLayer

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
        context.coordinator.session = session

        return containerView
    }

    func updateUIView(_ uiView: CameraPreviewView, context: Context) {}

    static func dismantleUIView(_ uiView: CameraPreviewView, coordinator: Coordinator) {
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
            UIAccessibility.post(notification: .announcement, argument: NSLocalizedString("QR code scanned", comment: ""))
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

// MARK: - Text Scanner View (Vision OCR)

/// AVFoundation + Vision text recognition scanner.
/// Continuously processes camera frames and returns detected text strings.
struct TextScannerView: UIViewRepresentable {
    let onTextsDetected: ([String]) -> Void
    @Binding var isFrozen: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(onTextsDetected: onTextsDetected)
    }

    func makeUIView(context: Context) -> CameraPreviewView {
        let containerView = CameraPreviewView(frame: .zero)
        containerView.backgroundColor = .black
        containerView.accessibilityLabel = NSLocalizedString("Camera preview for text scanning", comment: "")
        containerView.isAccessibilityElement = true

        guard let device = AVCaptureDevice.default(for: .video) else {
            addErrorLabel(to: containerView, text: String(localized: "Camera not available"))
            return containerView
        }

        guard let input = try? AVCaptureDeviceInput(device: device) else {
            addErrorLabel(to: containerView, text: String(localized: "Cannot access camera"))
            return containerView
        }

        let session = AVCaptureSession()
        session.sessionPreset = .high
        session.addInput(input)

        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
        ]
        videoOutput.setSampleBufferDelegate(context.coordinator, queue: context.coordinator.processingQueue)
        session.addOutput(videoOutput)

        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        containerView.previewLayer = previewLayer
        context.coordinator.previewLayer = previewLayer

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
        context.coordinator.session = session

        return containerView
    }

    func updateUIView(_ uiView: CameraPreviewView, context: Context) {
        context.coordinator.isFrozen = isFrozen
    }

    static func dismantleUIView(_ uiView: CameraPreviewView, coordinator: Coordinator) {
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

    class Coordinator: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
        let onTextsDetected: ([String]) -> Void
        var session: AVCaptureSession?
        var previewLayer: AVCaptureVideoPreviewLayer?
        var isFrozen = false
        let processingQueue = DispatchQueue(label: "com.accbot.textscanner", qos: .userInitiated)
        private var isProcessing = false

        init(onTextsDetected: @escaping ([String]) -> Void) {
            self.onTextsDetected = onTextsDetected
        }

        func captureOutput(
            _ output: AVCaptureOutput,
            didOutput sampleBuffer: CMSampleBuffer,
            from connection: AVCaptureConnection
        ) {
            guard !isFrozen, !isProcessing else { return }
            isProcessing = true

            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
                isProcessing = false
                return
            }

            let request = VNRecognizeTextRequest { [weak self] request, error in
                defer { self?.isProcessing = false }
                guard error == nil,
                      let observations = request.results as? [VNRecognizedTextObservation]
                else { return }

                // Filter: 8-256 alphanumeric characters, within center region
                let texts = observations.compactMap { observation -> String? in
                    guard let candidate = observation.topCandidates(1).first else { return nil }
                    let text = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)

                    // Filter by length -- credential-like strings
                    guard text.count >= 8 && text.count <= 256 else { return nil }

                    // Must be mostly alphanumeric (allow - and _)
                    let alphanumericSet = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_=+/"))
                    let cleaned = text.unicodeScalars.filter { alphanumericSet.contains($0) }
                    guard Double(cleaned.count) / Double(text.unicodeScalars.count) > 0.8 else { return nil }

                    // Filter by vertical position -- focus on center 60% of frame
                    let midY = observation.boundingBox.midY
                    guard midY > 0.2 && midY < 0.8 else { return nil }

                    return text
                }

                if !texts.isEmpty {
                    DispatchQueue.main.async {
                        self?.onTextsDetected(texts)
                    }
                }
            }
            request.recognitionLevel = .fast
            request.usesLanguageCorrection = false

            let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
            try? handler.perform([request])
        }
    }
}

// MARK: - QR Scanner Sheet (Updated with Mode Toggle)

/// Convenience wrapper that presents the scanner in a sheet with an
/// overlay frame, mode toggle, and a cancel button.
struct QrScannerSheet: View {
    let title: String
    let onScanned: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors

    @State private var scanMode: ScanMode = .qr
    @State private var isFrozen = false
    @State private var detectedTexts: [String] = []

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Mode toggle
                Picker(String(localized: "Scan Mode"), selection: $scanMode) {
                    ForEach(ScanMode.allCases, id: \.self) { mode in
                        Label(mode.label, systemImage: mode.systemImage)
                            .tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
                .onChange(of: scanMode) { _ in
                    // Reset state when switching modes
                    isFrozen = false
                    detectedTexts = []
                }

                // Camera preview
                ZStack {
                    if scanMode == .qr {
                        QrScannerView { code in
                            onScanned(code)
                            dismiss()
                        }
                        .ignoresSafeArea()

                        // QR scanning frame overlay
                        RoundedRectangle(cornerRadius: CornerRadius.lg)
                            .strokeBorder(colors.primary, lineWidth: 2)
                            .frame(width: min(UIScreen.main.bounds.width * 0.65, 280),
                                   height: min(UIScreen.main.bounds.width * 0.65, 280))
                            .accessibilityLabel(String(localized: "QR code scanning area"))
                            .accessibilityHint(String(localized: "Point camera at QR code inside the frame"))
                    } else {
                        TextScannerView(
                            onTextsDetected: { texts in
                                if !isFrozen {
                                    detectedTexts = texts
                                }
                            },
                            isFrozen: $isFrozen
                        )
                        .ignoresSafeArea()

                        // Text scanning reticle -- horizontal strip
                        VStack {
                            Spacer()
                            RoundedRectangle(cornerRadius: CornerRadius.sm)
                                .strokeBorder(colors.primary, lineWidth: 2)
                                .frame(height: 80)
                                .padding(.horizontal, Spacing.xxl)
                            Spacer()
                        }

                        // Freeze/unfreeze button
                        VStack {
                            Spacer()
                            Button {
                                if !UIAccessibility.isReduceMotionEnabled {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                }
                                isFrozen.toggle()
                                if !isFrozen {
                                    detectedTexts = []
                                }
                            } label: {
                                Image(systemName: isFrozen ? "play.circle.fill" : "pause.circle.fill")
                                    .font(AccBotFonts.displayLarge)
                                    .foregroundStyle(.white)
                                    .shadow(radius: 4)
                            }
                            .accessibilityLabel(isFrozen
                                ? String(localized: "Resume scanning")
                                : String(localized: "Freeze scanning"))
                            .padding(.bottom, Spacing.md)
                        }
                    }
                }
                .frame(maxHeight: scanMode == .text ? UIScreen.main.bounds.height * 0.45 : .infinity)

                // Detected texts list (text mode only)
                if scanMode == .text {
                    detectedTextsList
                }
            }
            .background(Color.black)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(String(localized: "Cancel")) { dismiss() }
                        .foregroundStyle(colors.primary)
                }
            }
        }
    }

    private var detectedTextsList: some View {
        ScrollView {
            if detectedTexts.isEmpty {
                VStack(spacing: Spacing.sm) {
                    Image(systemName: "text.viewfinder")
                        .font(AccBotFonts.iconMedium)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text(String(localized: "Point camera at text to scan"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.xxl)
            } else {
                LazyVStack(spacing: Spacing.sm) {
                    ForEach(detectedTexts, id: \.self) { text in
                        Button {
                            onScanned(text)
                            dismiss()
                        } label: {
                            HStack {
                                Text(text)
                                    .font(AccBotFonts.mono)
                                    .foregroundStyle(.white)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                Spacer()
                                Image(systemName: "arrow.right.circle.fill")
                                    .foregroundStyle(colors.primary)
                            }
                            .padding(Spacing.md)
                            .background(Color.white.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        }
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
        }
        .frame(maxHeight: .infinity)
    }
}

// MARK: - Scan Target Field

/// Represents a credential field that can be scanned in the multi-field scanner.
struct ScanTargetField: Identifiable {
    let id: String   // key like "apiKey"
    let label: String // display like "API Key"
}

// MARK: - Multi-Field Scanner Sheet

/// Scanner sheet that supports scanning multiple credential fields at once.
/// Used by AddExchangeView and ExchangeDetailView for "Scan All Credentials".
struct MultiFieldScannerSheet: View {
    let title: String
    let fields: [ScanTargetField]
    let onResult: ([String: String]) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var assignments: [String: String] = [:]
    @State private var activeFieldKey: String?
    @State private var scanMode: ScanMode = .qr
    @State private var isFrozen = false
    @State private var detectedTexts: [String] = []
    @State private var pulseOpacity: Double = 1.0
    @State private var assignedScale: [String: CGFloat] = [:]

    private var allAssigned: Bool {
        fields.allSatisfy { assignments[$0.id] != nil }
    }

    private var assignedCount: Int {
        fields.filter { assignments[$0.id] != nil }.count
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Mode toggle
                Picker(String(localized: "Scan Mode"), selection: $scanMode) {
                    ForEach(ScanMode.allCases, id: \.self) { mode in
                        Label(mode.label, systemImage: mode.systemImage)
                            .tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
                .onChange(of: scanMode) { _ in
                    isFrozen = false
                    detectedTexts = []
                }

                // Camera preview area (60% of screen)
                ZStack {
                    if scanMode == .qr {
                        QrScannerView { code in
                            handleQrScan(code)
                        }
                        .ignoresSafeArea()

                        RoundedRectangle(cornerRadius: CornerRadius.lg)
                            .strokeBorder(colors.primary, lineWidth: 2)
                            .frame(width: min(UIScreen.main.bounds.width * 0.65, 280),
                                   height: min(UIScreen.main.bounds.width * 0.65, 280))
                    } else {
                        TextScannerView(
                            onTextsDetected: { texts in
                                if !isFrozen {
                                    detectedTexts = texts
                                }
                            },
                            isFrozen: $isFrozen
                        )
                        .ignoresSafeArea()

                        // Text reticle
                        VStack {
                            Spacer()
                            RoundedRectangle(cornerRadius: CornerRadius.sm)
                                .strokeBorder(colors.primary, lineWidth: 2)
                                .frame(height: 80)
                                .padding(.horizontal, Spacing.xxl)
                            Spacer()
                        }

                        // Freeze button
                        VStack {
                            Spacer()
                            Button {
                                isFrozen.toggle()
                                if !isFrozen {
                                    detectedTexts = []
                                }
                            } label: {
                                Image(systemName: isFrozen ? "play.circle.fill" : "pause.circle.fill")
                                    .font(AccBotFonts.displayLarge)
                                    .foregroundStyle(.white)
                                    .shadow(radius: 4)
                            }
                            .accessibilityLabel(isFrozen
                                ? String(localized: "Resume scanning")
                                : String(localized: "Freeze scanning"))
                            .padding(.bottom, Spacing.md)
                        }
                    }
                }
                .frame(height: scanMode == .text
                    ? max(UIScreen.main.bounds.height * 0.25, 200)
                    : max(UIScreen.main.bounds.height * 0.35, 260))

                // Field assignment area
                fieldAssignmentArea
            }
            .background(Color.black)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(String(localized: "Cancel")) { dismiss() }
                        .foregroundStyle(colors.primary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        onResult(assignments)
                        dismiss()
                    } label: {
                        Text(String(localized: "Done (\(assignedCount)/\(fields.count))"))
                            .fontWeight(.semibold)
                    }
                    .foregroundStyle(colors.primary)
                    .disabled(assignedCount == 0)
                }
            }
            .onAppear {
                // Auto-select first field
                activeFieldKey = fields.first?.id
                startPulseAnimation()
            }
        }
    }

    // MARK: - Field Assignment Area

    private var fieldAssignmentArea: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            // Instruction text
            if allAssigned {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(colors.primary)
                    Text(String(localized: "All fields assigned!"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.primary)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.sm)
            } else if let activeKey = activeFieldKey,
                      let activeField = fields.first(where: { $0.id == activeKey }) {
                Text(String(localized: "Now scanning: \(activeField.label)"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.top, Spacing.sm)
            }

            // Field chips (horizontal scroll)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(fields) { field in
                        fieldChip(field)
                    }
                }
                .padding(.horizontal, Spacing.lg)
            }
            .padding(.vertical, Spacing.xs)

            // Detected text cards (text mode)
            if scanMode == .text {
                ScrollView {
                    if detectedTexts.isEmpty && !isFrozen {
                        VStack(spacing: Spacing.sm) {
                            Image(systemName: "text.viewfinder")
                                .font(AccBotFonts.iconMedium)
                                .foregroundStyle(colors.onSurfaceVariant)
                            Text(String(localized: "Point camera at text to scan"))
                                .font(AccBotFonts.bodySmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                    } else {
                        LazyVStack(spacing: Spacing.sm) {
                            ForEach(detectedTexts, id: \.self) { text in
                                Button {
                                    assignTextToActiveField(text)
                                } label: {
                                    HStack {
                                        Text(text)
                                            .font(AccBotFonts.mono)
                                            .foregroundStyle(.white)
                                            .lineLimit(2)
                                            .multilineTextAlignment(.leading)
                                        Spacer()
                                        Image(systemName: "plus.circle.fill")
                                            .foregroundStyle(colors.primary)
                                    }
                                    .padding(Spacing.md)
                                    .background(Color.white.opacity(0.15))
                                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                                }
                                .disabled(activeFieldKey == nil)
                            }
                        }
                        .padding(.horizontal, Spacing.lg)
                    }
                }
                .frame(maxHeight: .infinity)
            }
        }
        .frame(maxHeight: .infinity)
    }

    // MARK: - Field Chip

    private func fieldChip(_ field: ScanTargetField) -> some View {
        let isActive = activeFieldKey == field.id
        let isAssigned = assignments[field.id] != nil
        let scale = assignedScale[field.id] ?? 1.0

        return Button {
            if !isAssigned {
                activeFieldKey = field.id
            }
        } label: {
            HStack(spacing: Spacing.xs) {
                if isAssigned {
                    Image(systemName: "checkmark.circle.fill")
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.primary)
                }

                Text(field.label)
                    .font(AccBotFonts.label)
                    .foregroundStyle(isAssigned ? colors.primary : (isActive ? .white : colors.onSurfaceVariant))

                if isAssigned, let value = assignments[field.id] {
                    Text(String(value.prefix(12)) + (value.count > 12 ? "..." : ""))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.primary)
                }
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: 44)
            .background(
                isAssigned ? colors.primary.opacity(0.15) :
                    (isActive ? colors.primary.opacity(0.3) : Color.white.opacity(0.15))
            )
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.sm)
                    .stroke(
                        isAssigned ? colors.primary :
                            (isActive ? colors.primary.opacity(pulseOpacity) : Color.clear),
                        lineWidth: isActive ? 2 : 1
                    )
            )
            .scaleEffect(scale)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(field.label)
        .accessibilityValue(
            isAssigned ? String(localized: "Assigned") :
            (isActive ? String(localized: "Scanning now") : String(localized: "Pending"))
        )
    }

    // MARK: - Actions

    private func handleQrScan(_ code: String) {
        // In QR mode, try JSON parsing first (backward compat)
        if let data = code.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: String] {
            // Map JSON keys to field IDs
            let keyMappings: [String: [String]] = [
                "apiKey": ["apiKey", "publicKey", "key"],
                "apiSecret": ["apiSecret", "privateKey", "secret"],
                "passphrase": ["passphrase"],
                "clientId": ["clientId"],
            ]

            for field in fields {
                if let mappings = keyMappings[field.id] {
                    for mapping in mappings {
                        if let value = json[mapping] {
                            assignments[field.id] = value.trimmingCharacters(in: .whitespacesAndNewlines)
                            break
                        }
                    }
                }
            }

            // If all fields assigned, auto-complete
            if allAssigned {
                onResult(assignments)
                dismiss()
            }
        } else if let activeKey = activeFieldKey {
            // Single value -- assign to active field
            assignments[activeKey] = code.trimmingCharacters(in: .whitespacesAndNewlines)
            animateAssignment(activeKey)
            advanceToNextField()
        }
    }

    private func assignTextToActiveField(_ text: String) {
        guard let activeKey = activeFieldKey else { return }
        assignments[activeKey] = text.trimmingCharacters(in: .whitespacesAndNewlines)
        animateAssignment(activeKey)
        advanceToNextField()
    }

    private func advanceToNextField() {
        guard let currentIndex = fields.firstIndex(where: { $0.id == activeFieldKey }) else { return }

        // Find next unassigned field
        let remaining = fields.indices.filter { $0 > currentIndex && assignments[fields[$0].id] == nil }
        if let nextIndex = remaining.first {
            activeFieldKey = fields[nextIndex].id
        } else {
            // Wrap around
            let earlier = fields.indices.filter { assignments[fields[$0].id] == nil }
            activeFieldKey = earlier.first.map { fields[$0].id }
        }
    }

    private func animateAssignment(_ fieldId: String) {
        if let field = fields.first(where: { $0.id == fieldId }) {
            UIAccessibility.post(
                notification: .announcement,
                argument: String(localized: "\(field.label) assigned")
            )
        }
        AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
        guard !reduceMotion else { return }
        withAnimation(.spring(response: 0.3)) {
            assignedScale[fieldId] = 1.15
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            withAnimation(.spring(response: 0.3)) {
                assignedScale[fieldId] = 1.0
            }
        }
    }

    private func startPulseAnimation() {
        guard !reduceMotion else { return }
        withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
            pulseOpacity = 0.3
        }
    }
}

// MARK: - Preview

#Preview("QR Scanner Sheet") {
    QrScannerSheet(
        title: "Scan Wallet QR",
        onScanned: { _ in }
    )
}

#Preview("Multi-Field Scanner") {
    MultiFieldScannerSheet(
        title: "Scan Credentials",
        fields: [
            ScanTargetField(id: "apiKey", label: "API Key"),
            ScanTargetField(id: "apiSecret", label: "API Secret"),
        ],
        onResult: { _ in }
    )
}
