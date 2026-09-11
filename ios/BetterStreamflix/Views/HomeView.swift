import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var app
    @State private var catalog: [CategoryRow] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: EmberTheme.spaceLG) {
                header
                // Featured must sit above Continue Watching.
                if !isLoading, errorMessage == nil,
                   let featured = catalog.first(where: \.isFeatured),
                   let hero = featured.items.first {
                    FeaturedHero(item: hero, subtitle: featured.title)
                }
                if !app.library.continueWatching.isEmpty {
                    continueRow
                }
                if isLoading {
                    ProgressView("Loading catalog…")
                        .frame(maxWidth: .infinity, minHeight: 220)
                } else if let errorMessage {
                    ContentUnavailableView(
                        "Couldn’t load home",
                        systemImage: "wifi.exclamationmark",
                        description: Text(errorMessage)
                    )
                    Button("Retry") { Task { await load() } }
                        .buttonStyle(.borderedProminent)
                        .tint(EmberTheme.accent)
                } else {
                    ForEach(catalog.filter { !$0.isFeatured }) { section in
                        CatalogRow(section: section)
                    }
                }
            }
            .padding(.horizontal, EmberTheme.spaceMD)
            .padding(.bottom, 96)
        }
        .emberBackground()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("BetterStreamflix")
                    .font(.system(.headline, design: .rounded).weight(.bold))
            }
        }
        .task(id: app.activeProvider.id) {
            catalog = []
            await load()
        }
        .navigationDestination(for: MediaItem.self) { item in
            DetailView(item: item)
        }
        .refreshable { await load() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(app.activeProvider.name)
                .font(.system(.largeTitle, design: .rounded).weight(.bold))
                .foregroundStyle(.white)
            Text("Browse · \(app.activeProvider.language.uppercased())")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.7))
        }
        .padding(.top, EmberTheme.spaceMD)
    }

    private var continueRow: some View {
        VStack(alignment: .leading, spacing: EmberTheme.spaceSM) {
            Text("Weitersehen")
                .font(.system(.title3, design: .rounded).weight(.semibold))
                .foregroundStyle(.white)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: EmberTheme.spaceSM) {
                    ForEach(app.library.continueWatching) { entry in
                        NavigationLink {
                            DetailView(
                                item: entry.asMediaItem,
                                forcedProviderID: entry.providerID
                            )
                        } label: {
                            PosterCard(item: entry.asMediaItem, progress: entry.progress)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    @MainActor
    private func load() async {
        isLoading = catalog.isEmpty
        errorMessage = nil
        do {
            let rows = try await app.activeProvider.home()
            catalog = rows
            isLoading = false
        } catch {
            // Android HomeViewModel: keep disk/in-memory catalog if live fetch fails.
            if !catalog.isEmpty {
                isLoading = false
                return
            }
            errorMessage = "\(app.activeProvider.name): \(error.localizedDescription)"
            isLoading = false
        }
    }
}

private struct FeaturedHero: View {
    let item: MediaItem
    let subtitle: String

    var body: some View {
        NavigationLink(value: item) {
            ZStack(alignment: .bottomLeading) {
                Color.clear
                    .frame(maxWidth: .infinity)
                    .frame(height: 220)
                    .overlay {
                        AsyncImage(url: item.bannerURL ?? item.posterURL) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .scaledToFill()
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                            default:
                                EmberTheme.surfaceElevated
                            }
                        }
                    }
                    .clipped()

                LinearGradient(
                    colors: [.clear, EmberTheme.background.opacity(0.95)],
                    startPoint: .center,
                    endPoint: .bottom
                )

                VStack(alignment: .leading, spacing: 6) {
                    Text(subtitle.uppercased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(EmberTheme.gold)
                    Text(item.title)
                        .font(.system(.title, design: .rounded).weight(.bold))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                }
                .padding(EmberTheme.spaceMD)
            }
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusLG, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct CatalogRow: View {
    let section: CategoryRow

    var body: some View {
        VStack(alignment: .leading, spacing: EmberTheme.spaceSM) {
            Text(section.title)
                .font(.system(.title3, design: .rounded).weight(.semibold))
                .foregroundStyle(.white)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: EmberTheme.spaceSM) {
                    ForEach(section.items) { item in
                        NavigationLink(value: item) {
                            PosterCard(item: item)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

struct PosterCard: View {
    let item: MediaItem
    var progress: Double? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .bottom) {
                Color.clear
                    .frame(width: 128, height: 192)
                    .overlay(alignment: .top) {
                        AsyncImage(url: item.posterURL ?? item.bannerURL) { phase in
                            switch phase {
                            case .success(let image):
                                // Top-aligned fill reduces “zoomed into faces” crop on posters.
                                image
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 128, height: 192, alignment: .top)
                            case .failure:
                                placeholder
                            case .empty:
                                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                            @unknown default:
                                placeholder
                            }
                        }
                    }
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusMD, style: .continuous))

                if item.isLive {
                    Text("LIVE")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(EmberTheme.accent, in: Capsule())
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                        .padding(6)
                }

                if let progress, progress > 0 {
                    GeometryReader { geo in
                        Rectangle()
                            .fill(EmberTheme.accent)
                            .frame(width: geo.size.width * min(max(progress, 0), 1), height: 3)
                            .frame(maxHeight: .infinity, alignment: .bottom)
                    }
                    .frame(width: 128, height: 192)
                    .allowsHitTesting(false)
                }
            }

            Text(item.title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(2)
                .frame(width: 128, alignment: .leading)
        }
    }

    private var placeholder: some View {
        ZStack {
            EmberTheme.surfaceElevated
            Image(systemName: "film")
                .foregroundStyle(.white.opacity(0.45))
        }
    }
}
