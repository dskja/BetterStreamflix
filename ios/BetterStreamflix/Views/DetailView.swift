import SwiftUI
import WebKit

struct DetailView: View {
    @Environment(AppModel.self) private var app
    let item: MediaItem
    var forcedProviderID: String? = nil

    @State private var detail: ShowDetail?
    @State private var episodes: [EpisodeInfo] = []
    @State private var selectedSeason: SeasonInfo?
    @State private var isLoading = true
    @State private var isLoadingEpisodes = false
    @State private var errorMessage: String?
    @State private var isResolving = false
    @State private var resolveError: String?
    @State private var playerItem: PlayerLaunch?
    @State private var pendingSources: [StreamSource] = []
    @State private var showSourcePicker = false
    @State private var challengeURL: IdentifiedURL?
    @State private var pendingTitle = ""
    @State private var pendingSeasonID: String?
    @State private var pendingEpisodeID: String?

    private var provider: any CatalogProvider {
        if let forcedProviderID, let match = app.providers.first(where: { $0.id == forcedProviderID }) {
            return match
        }
        if let hint = item.providerHint, let match = app.providers.first(where: { $0.id == hint }) {
            return match
        }
        return app.activeProvider
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: EmberTheme.spaceLG) {
                hero
                if isLoading {
                    ProgressView("Loading details…")
                        .frame(maxWidth: .infinity, minHeight: 160)
                } else if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                    Button("Retry") { Task { await load() } }
                } else if let detail {
                    meta(detail)
                    detailBody(detail)
                }
            }
            .padding(.horizontal, EmberTheme.spaceMD)
            .padding(.bottom, EmberTheme.spaceXL)
        }
        .emberBackground()
        .navigationTitle(item.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    app.library.toggleFavorite(item, providerID: provider.id)
                } label: {
                    Image(systemName: app.library.isFavorite(item, providerID: provider.id) ? "bookmark.fill" : "bookmark")
                        .foregroundStyle(EmberTheme.accent)
                }
            }
        }
        .task { await load() }
        .fullScreenCover(item: $playerItem) { launch in
            PlayerView(url: launch.url, title: launch.title, headers: launch.headers)
        }
        .sheet(isPresented: $showSourcePicker) {
            SourcePickerSheet(sources: pendingSources) { source in
                showSourcePicker = false
                Task { await resolveAndPlay(source, title: pendingTitle) }
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $challengeURL) { identified in
            ChallengeGateView(url: identified.url) { finalURL in
                challengeURL = nil
                Task { await finishChallenge(finalURL: finalURL, title: pendingTitle) }
            }
        }
        .alert("Playback", isPresented: Binding(
            get: { resolveError != nil },
            set: { if !$0 { resolveError = nil } }
        )) {
            Button("OK", role: .cancel) { resolveError = nil }
        } message: {
            Text(resolveError ?? "")
        }
    }

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            AsyncImage(url: item.bannerURL ?? item.posterURL ?? detail?.bannerURL ?? detail?.posterURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                default:
                    EmberTheme.surfaceElevated
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 360)
            .clipped()

            LinearGradient(
                colors: [.clear, EmberTheme.background.opacity(0.95)],
                startPoint: .top,
                endPoint: .bottom
            )

            VStack(alignment: .leading, spacing: 8) {
                Text(detail?.title ?? item.title)
                    .font(.system(.largeTitle, design: .rounded).weight(.bold))
                    .foregroundStyle(.white)
                HStack(spacing: 10) {
                    Text(item.kind == .tvShow ? "Series" : "Movie")
                    if let year = detail?.year ?? item.year { Text("· \(year)") }
                    if let rating = detail?.rating ?? item.rating {
                        Text("· ★ \(String(format: "%.1f", rating))")
                    }
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.white.opacity(0.75))
            }
            .padding(EmberTheme.spaceMD)
        }
        .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusLG, style: .continuous))
        .glassChrome(cornerRadius: EmberTheme.radiusLG)
    }

    @ViewBuilder
    private func meta(_ detail: ShowDetail) -> some View {
        if !detail.genres.isEmpty {
            Text(detail.genres.joined(separator: " · "))
                .font(.caption.weight(.semibold))
                .foregroundStyle(EmberTheme.gold)
        }
        if let overview = detail.overview, !overview.isEmpty {
            Text(overview)
                .font(.body)
                .foregroundStyle(.white.opacity(0.88))
        }
        if !detail.cast.isEmpty {
            Text("Cast: \(detail.cast.joined(separator: ", "))")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private func detailBody(_ detail: ShowDetail) -> some View {
        if detail.kind == .movie || detail.seasons.isEmpty {
            playButton(title: isResolving ? "Resolving…" : "Play") {
                await play(showId: detail.id, seasonId: nil, episodeId: nil, title: detail.title)
            }
        } else {
            seasonPicker(detail)
            episodeList(detail)
        }
    }

    private func seasonPicker(_ detail: ShowDetail) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(detail.seasons) { season in
                    Button(season.title.isEmpty ? "S\(season.number)" : season.title) {
                        selectedSeason = season
                        Task { await loadEpisodes(for: detail, season: season) }
                    }
                    .buttonStyle(.bordered)
                    .tint(selectedSeason?.id == season.id ? EmberTheme.accent : .white.opacity(0.5))
                }
            }
        }
    }

    private func episodeList(_ detail: ShowDetail) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(selectedSeason?.title ?? "Episodes")
                .font(.title3.weight(.semibold))
                .foregroundStyle(.white)

            if isLoadingEpisodes {
                ProgressView().frame(maxWidth: .infinity, minHeight: 80)
            } else {
                ForEach(episodes) { episode in
                    Button {
                        Task {
                            await play(
                                showId: detail.id,
                                seasonId: selectedSeason?.id,
                                episodeId: episode.id,
                                title: "\(detail.title) · E\(episode.number)"
                            )
                        }
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            if let thumb = episode.thumbnailURL {
                                AsyncImage(url: thumb) { phase in
                                    if case .success(let image) = phase {
                                        image.resizable().scaledToFill()
                                    } else {
                                        EmberTheme.surfaceElevated
                                    }
                                }
                                .frame(width: 96, height: 54)
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                            }
                            VStack(alignment: .leading, spacing: 4) {
                                Text("E\(episode.number) · \(episode.title)")
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .multilineTextAlignment(.leading)
                                if let overview = episode.overview, !overview.isEmpty {
                                    Text(overview)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                } else {
                                    Text("Tap to choose source")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Image(systemName: "play.circle.fill")
                                .font(.title2)
                                .foregroundStyle(EmberTheme.accent)
                        }
                        .padding(EmberTheme.spaceMD)
                        .glassChrome(cornerRadius: EmberTheme.radiusMD)
                    }
                    .buttonStyle(.plain)
                    .disabled(isResolving)
                }
            }
        }
    }

    private func playButton(title: String, action: @escaping @MainActor () async -> Void) -> some View {
        Button {
            Task { await action() }
        } label: {
            HStack {
                if isResolving { ProgressView().tint(.white) }
                else { Image(systemName: "play.fill") }
                Text(title).fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        .tint(EmberTheme.accent)
        .disabled(isResolving)
    }

    @MainActor
    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            let loaded = try await provider.detail(id: item.id, kind: item.kind)
            detail = loaded
            isLoading = false
            if let first = loaded.seasons.first {
                selectedSeason = first
                await loadEpisodes(for: loaded, season: first)
            }
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    @MainActor
    private func loadEpisodes(for detail: ShowDetail, season: SeasonInfo) async {
        isLoadingEpisodes = true
        defer { isLoadingEpisodes = false }
        do {
            episodes = try await provider.episodes(showId: detail.id, seasonId: season.id)
        } catch {
            episodes = []
            resolveError = error.localizedDescription
        }
    }

    @MainActor
    private func play(showId: String, seasonId: String?, episodeId: String?, title: String) async {
        isResolving = true
        defer { isResolving = false }
        pendingTitle = title
        pendingSeasonID = seasonId
        pendingEpisodeID = episodeId
        do {
            let streams = try await provider.streams(
                showId: showId,
                seasonId: seasonId,
                episodeId: episodeId,
                detail: detail
            )
            guard !streams.isEmpty else {
                resolveError = "No playable streams found."
                return
            }
            if streams.count == 1 {
                await resolveAndPlay(streams[0], title: title)
            } else {
                pendingSources = streams
                showSourcePicker = true
            }
        } catch {
            resolveError = error.localizedDescription
        }
    }

    @MainActor
    private func resolveAndPlay(_ source: StreamSource, title: String) async {
        isResolving = true
        defer { isResolving = false }
        do {
            if source.resolveKind == .serienstreamGate {
                challengeURL = IdentifiedURL(url: source.url)
                return
            }
            let url = try await StreamResolver.resolve(source)
            openPlayer(url: url, title: title, headers: source.headers)
        } catch let error as ProviderError {
            if case .streamGate = error {
                challengeURL = IdentifiedURL(url: source.url)
            } else {
                resolveError = error.localizedDescription
            }
        } catch {
            resolveError = error.localizedDescription
        }
    }

    @MainActor
    private func finishChallenge(finalURL: URL, title: String) async {
        if StreamResolver.isSerienStreamHost(finalURL) || finalURL.absoluteString.contains("/r?") {
            resolveError = "Challenge incomplete — stream gate still active."
            return
        }
        openPlayer(url: finalURL, title: title, headers: [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": SerienStreamProvider().baseURL.absoluteString,
        ])
    }

    @MainActor
    private func openPlayer(url: URL, title: String, headers: [String: String]) {
        app.library.recordProgress(
            item: item,
            providerID: provider.id,
            seasonID: pendingSeasonID,
            episodeID: pendingEpisodeID,
            progress: 0.05
        )
        playerItem = PlayerLaunch(url: url, title: title, headers: headers)
    }
}

struct PlayerLaunch: Identifiable {
    let id = UUID()
    let url: URL
    let title: String
    var headers: [String: String] = [:]
}

struct IdentifiedURL: Identifiable {
    let id = UUID()
    let url: URL
}

private struct SourcePickerSheet: View {
    let sources: [StreamSource]
    let onPick: (StreamSource) -> Void

    var body: some View {
        NavigationStack {
            List(sources) { source in
                Button {
                    onPick(source)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(source.name)
                            .foregroundStyle(.primary)
                        Text(source.resolveKind.rawValue)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Choose source")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct ChallengeGateView: View {
    let url: URL
    var onResolved: (URL) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ChallengeWebView(url: url) { finalURL in
                onResolved(finalURL)
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle("Verify stream")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Text("Complete the SerienStream check, then wait for redirect to the hoster.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial)
            }
        }
    }
}

struct ChallengeWebView: UIViewRepresentable {
    let url: URL
    var onResolved: (URL) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onResolved: onResolved)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.customUserAgent = HTTPClient.desktopUserAgent
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let onResolved: (URL) -> Void
        init(onResolved: @escaping (URL) -> Void) {
            self.onResolved = onResolved
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
            if let navURL = navigationAction.request.url,
               !StreamResolver.isSerienStreamHost(navURL),
               !navURL.absoluteString.contains("/r?") {
                onResolved(navURL)
                return .cancel
            }
            return .allow
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            if let navURL = webView.url,
               !StreamResolver.isSerienStreamHost(navURL),
               !navURL.absoluteString.contains("/r?") {
                onResolved(navURL)
            }
        }
    }
}
