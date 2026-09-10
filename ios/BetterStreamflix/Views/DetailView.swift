import SwiftUI

struct DetailView: View {
    @Environment(AppModel.self) private var app
    let item: MediaItem

    @State private var detail: ShowDetail?
    @State private var episodes: [EpisodeInfo] = []
    @State private var selectedSeason: SeasonInfo?
    @State private var isLoading = true
    @State private var isLoadingEpisodes = false
    @State private var errorMessage: String?
    @State private var isResolving = false
    @State private var resolveError: String?
    @State private var playerItem: PlayerLaunch?

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
                    detailBody(detail)
                }
            }
            .padding(.horizontal, EmberTheme.spaceMD)
            .padding(.bottom, EmberTheme.spaceXL)
        }
        .emberBackground()
        .navigationTitle(item.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .fullScreenCover(item: $playerItem) { launch in
            PlayerView(url: launch.url, title: launch.title)
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
            AsyncImage(url: item.bannerURL ?? item.posterURL) { phase in
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
                Text(item.title)
                    .font(.system(.largeTitle, design: .rounded).weight(.bold))
                    .foregroundStyle(.white)
                Text(item.kind == .tvShow ? "Series" : "Movie")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white.opacity(0.75))
            }
            .padding(EmberTheme.spaceMD)
        }
        .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusLG, style: .continuous))
        .glassChrome(cornerRadius: EmberTheme.radiusLG)
    }

    @ViewBuilder
    private func detailBody(_ detail: ShowDetail) -> some View {
        if let overview = detail.overview, !overview.isEmpty {
            Text(overview)
                .font(.body)
                .foregroundStyle(.white.opacity(0.88))
        }

        if detail.kind == .movie {
            playButton(title: "Play movie") {
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
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 80)
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
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("E\(episode.number) · \(episode.title)")
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .multilineTextAlignment(.leading)
                                Text("Tap to play")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
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
                if isResolving {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: "play.fill")
                }
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
            let loaded = try await app.activeProvider.detail(id: item.id, kind: item.kind)
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
            episodes = try await app.activeProvider.episodes(showId: detail.id, seasonId: season.id)
        } catch {
            episodes = []
            resolveError = error.localizedDescription
        }
    }

    @MainActor
    private func play(showId: String, seasonId: String?, episodeId: String?, title: String) async {
        isResolving = true
        defer { isResolving = false }
        do {
            let streams = try await app.activeProvider.streams(
                showId: showId,
                seasonId: seasonId,
                episodeId: episodeId
            )
            guard let first = streams.first else {
                resolveError = "No playable streams found."
                return
            }
            let url = try await StreamResolver.resolve(first)
            playerItem = PlayerLaunch(url: url, title: title)
        } catch {
            resolveError = error.localizedDescription
        }
    }
}

struct PlayerLaunch: Identifiable {
    let id = UUID()
    let url: URL
    let title: String
}
