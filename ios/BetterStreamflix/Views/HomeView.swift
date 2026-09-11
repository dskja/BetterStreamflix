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
                    ForEach(catalog) { section in
                        if section.isFeatured, let hero = section.items.first {
                            FeaturedHero(item: hero, subtitle: section.title)
                        } else {
                            CatalogRow(section: section)
                        }
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
            errorMessage = error.localizedDescription
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
                AsyncImage(url: item.bannerURL ?? item.posterURL) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        EmberTheme.surfaceElevated
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 260)
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
            .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusLG, style: .continuous))
        }
        .buttonStyle(.plain)
        .navigationDestination(for: MediaItem.self) { item in
            DetailView(item: item)
        }
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
        .navigationDestination(for: MediaItem.self) { item in
            DetailView(item: item)
        }
    }
}

struct PosterCard: View {
    let item: MediaItem
    var progress: Double? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .bottom) {
                AsyncImage(url: item.posterURL) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .failure:
                        placeholder
                    case .empty:
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    @unknown default:
                        placeholder
                    }
                }
                .frame(width: 128, height: 192)
                .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusMD, style: .continuous))

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
