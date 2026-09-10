import AVFoundation
import AVKit
import SwiftUI

struct PlayerView: View {
    let url: URL
    let title: String
    var headers: [String: String] = [:]
    @Environment(\.dismiss) private var dismiss

    @State private var player: AVPlayer?
    @State private var isPlaying = true
    @State private var showControls = true
    @State private var statusText = "Loading…"
    @State private var hasError = false
    @State private var duration: Double = 0
    @State private var current: Double = 0
    @State private var hideTask: Task<Void, Never>?
    @State private var timeObserver: Any?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let player, !hasError {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture { toggleControls() }
            }

            if hasError {
                VStack(spacing: 16) {
                    Image(systemName: "play.slash.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.white.opacity(0.7))
                    Text("Playback failed")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)
                    Text(statusText)
                        .font(.footnote)
                        .foregroundStyle(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    Button("Retry") { setupPlayer(reset: true) }
                        .buttonStyle(.borderedProminent)
                        .tint(EmberTheme.accent)
                }
            } else if player == nil {
                ProgressView(statusText)
                    .tint(.white)
            }

            if showControls {
                VStack {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(title)
                                .font(.headline)
                                .foregroundStyle(.white)
                                .lineLimit(2)
                            Text(statusText)
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.7))
                        }
                        Spacer()
                        Button { dismiss() } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title)
                                .symbolRenderingMode(.hierarchical)
                                .foregroundStyle(.white)
                        }
                        .accessibilityLabel("Close player")
                    }
                    .padding()
                    .background(
                        LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom)
                    )

                    Spacer()

                    VStack(spacing: 12) {
                        Slider(
                            value: Binding(
                                get: { current },
                                set: { seek(to: $0) }
                            ),
                            in: 0...max(duration, 1)
                        )
                        .tint(EmberTheme.accent)

                        HStack {
                            Text(format(current)).font(.caption2.monospacedDigit()).foregroundStyle(.white.opacity(0.8))
                            Spacer()
                            Button { skip(seconds: -10) } label: {
                                Image(systemName: "gobackward.10").font(.title2).foregroundStyle(.white)
                            }
                            Button { togglePlay() } label: {
                                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                    .font(.system(size: 44))
                                    .foregroundStyle(.white)
                            }
                            Button { skip(seconds: 10) } label: {
                                Image(systemName: "goforward.10").font(.title2).foregroundStyle(.white)
                            }
                            Spacer()
                            Text(format(duration)).font(.caption2.monospacedDigit()).foregroundStyle(.white.opacity(0.8))
                        }
                    }
                    .padding()
                    .background(
                        LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .top, endPoint: .bottom)
                    )
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden(true)
        .onAppear {
            setupPlayer(reset: false)
            scheduleHide()
        }
        .onDisappear { teardown() }
    }

    private func setupPlayer(reset: Bool) {
        if reset { teardown() }
        hasError = false
        statusText = "Connecting…"

        let item: AVPlayerItem
        if headers.isEmpty {
            item = AVPlayerItem(url: url)
        } else {
            let options = ["AVURLAssetHTTPHeaderFieldsKey": headers]
            let asset = AVURLAsset(url: url, options: options)
            item = AVPlayerItem(asset: asset)
        }

        let newPlayer = AVPlayer(playerItem: item)
        player = newPlayer
        newPlayer.play()
        isPlaying = true
        statusText = "Playing"

        timeObserver = newPlayer.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { time in
            current = time.seconds.isFinite ? time.seconds : 0
            if let d = newPlayer.currentItem?.duration.seconds, d.isFinite {
                duration = d
            }
            if newPlayer.currentItem?.status == .failed {
                hasError = true
                statusText = newPlayer.currentItem?.error?.localizedDescription ?? "Stream error"
            }
        }
    }

    private func teardown() {
        if let timeObserver, let player {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        player = nil
        hideTask?.cancel()
    }

    private func togglePlay() {
        guard let player else { return }
        if isPlaying {
            player.pause()
        } else {
            player.play()
        }
        isPlaying.toggle()
        scheduleHide()
    }

    private func skip(seconds: Double) {
        guard let player else { return }
        let target = max(0, min(duration, current + seconds))
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600))
        scheduleHide()
    }

    private func seek(to value: Double) {
        player?.seek(to: CMTime(seconds: value, preferredTimescale: 600))
        scheduleHide()
    }

    private func toggleControls() {
        showControls.toggle()
        if showControls { scheduleHide() }
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            if isPlaying { showControls = false }
        }
    }

    private func format(_ value: Double) -> String {
        guard value.isFinite else { return "0:00" }
        let total = Int(value)
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
