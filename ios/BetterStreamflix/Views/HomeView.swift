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
                        CatalogRow(section: section)
                    }
                }
            }
            .padding(.horizontal, EmberTheme.spaceMD)
            .padding(.bottom, EmberTheme.spaceXL)
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
            await load()
        }
        .refreshable { await load() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(app.activeProvider.name)
                .font(.system(.largeTitle, design: .rounded).weight(.bold))
                .foregroundStyle(.white)
            Text("Liquid Glass beta · German catalogs")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.7))
        }
        .padding(.top, EmberTheme.spaceMD)
        .padding(.bottom, EmberTheme.spaceSM)
    }

    @MainActor
    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            catalog = try await app.activeProvider.home()
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: item.posterURL) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                case .failure:
                    placeholder
                case .empty:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                @unknown default:
                    placeholder
                }
            }
            .frame(width: 128, height: 192)
            .clipShape(RoundedRectangle(cornerRadius: EmberTheme.radiusMD, style: .continuous))
            .glassChrome(cornerRadius: EmberTheme.radiusMD)

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
