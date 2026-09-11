import SwiftUI

struct SearchView: View {
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var results: [MediaItem] = []
    @State private var trending: [MediaItem] = []
    @State private var recentQueries: [String] = UserDefaults.standard.stringArray(forKey: "search.recents") ?? []
    @State private var isSearching = false
    @State private var isLoadingTrending = false
    @State private var errorMessage: String?
    @State private var searchTask: Task<Void, Never>?

    private var chips: [String] {
        switch app.activeProvider.id {
        case "plutotv-de", "pluto-de", "plutotv":
            return ["Live", "Nachrichten", "Sport", "Filme", "Serien", "Kinder", "Doku"]
        case "aniworld", "animeworld":
            return ["Action", "Romance", "Fantasy", "Comedy", "Drama", "Horror", "Slice of Life"]
        case "serienstream", "s.to":
            return ["Serien", "Action", "Drama", "Thriller", "Comedy", "Krimi", "Sci-Fi", "Horror"]
        case "filmo", "filmpalast", "kinoger", "megakino", "hdfilme", "einschalten":
            return ["Filme", "Action", "Komödie", "Drama", "Thriller", "Horror", "Anime", "Krimi"]
        default:
            return ["Filme", "Serien", "Action", "Comedy", "Drama", "Thriller", "Anime", "Horror"]
        }
    }

    var body: some View {
        List {
            if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                emptyBrowse
            } else if isSearching {
                HStack {
                    ProgressView()
                    Text("Searching…").foregroundStyle(.secondary)
                }
                .listRowBackground(Color.clear)
            } else if let errorMessage {
                Text(errorMessage).foregroundStyle(.red).listRowBackground(Color.clear)
            } else if results.isEmpty {
                ContentUnavailableView.search(text: query).listRowBackground(Color.clear)
            } else {
                Section("Results") {
                    ForEach(results) { item in
                        resultRow(item)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .emberBackground()
        .navigationTitle("Search")
        .navigationDestination(for: MediaItem.self) { item in
            DetailView(item: item)
        }
        .searchable(text: $query, prompt: "Titles on \(app.activeProvider.name)")
        .onChange(of: query) { _, newValue in
            searchTask?.cancel()
            searchTask = Task {
                try? await Task.sleep(for: .milliseconds(350))
                guard !Task.isCancelled else { return }
                await runSearch(newValue)
            }
        }
        .onChange(of: app.selectedProviderID) { _, _ in
            Task {
                await loadTrending()
                await runSearch(query)
            }
        }
        .task { await loadTrending() }
    }

    @ViewBuilder
    private var emptyBrowse: some View {
        if !recentQueries.isEmpty {
            Section("Recent") {
                ForEach(recentQueries, id: \.self) { recent in
                    Button {
                        query = recent
                    } label: {
                        Label(recent, systemImage: "clock.arrow.circlepath")
                            .foregroundStyle(.white)
                    }
                    .listRowBackground(Color.clear)
                }
                Button("Clear recents", role: .destructive) {
                    recentQueries = []
                    UserDefaults.standard.removeObject(forKey: "search.recents")
                }
                .listRowBackground(Color.clear)
            }
        }

        Section("Quick filters") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(chips, id: \.self) { chip in
                        Button(chip) { query = chip }
                            .buttonStyle(.bordered)
                            .tint(EmberTheme.accent)
                    }
                }
                .padding(.vertical, 4)
            }
            .listRowBackground(Color.clear)
        }

        Section("Trending on \(app.activeProvider.name)") {
            if isLoadingTrending {
                ProgressView().listRowBackground(Color.clear)
            } else if trending.isEmpty {
                Text("No trending titles yet.")
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(trending) { item in
                    resultRow(item)
                }
            }
        }
    }

    private func resultRow(_ item: MediaItem) -> some View {
        NavigationLink(value: item) {
            HStack(spacing: 12) {
                AsyncImage(url: item.posterURL) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        EmberTheme.surfaceElevated
                    }
                }
                .frame(width: 52, height: 78)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text(item.kind == .tvShow ? "Series" : "Movie")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listRowBackground(Color.clear)
    }

    @MainActor
    private func runSearch(_ raw: String) async {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            results = []
            errorMessage = nil
            isSearching = false
            return
        }
        isSearching = true
        errorMessage = nil
        do {
            results = try await app.activeProvider.search(query: trimmed)
            remember(trimmed)
            isSearching = false
        } catch {
            errorMessage = error.localizedDescription
            results = []
            isSearching = false
        }
    }

    @MainActor
    private func loadTrending() async {
        isLoadingTrending = true
        defer { isLoadingTrending = false }
        do {
            let home = try await app.activeProvider.home()
            trending = Array(home.flatMap(\.items).prefix(18))
        } catch {
            trending = []
        }
    }

    private func remember(_ query: String) {
        var next = recentQueries.filter { $0.caseInsensitiveCompare(query) != .orderedSame }
        next.insert(query, at: 0)
        recentQueries = Array(next.prefix(8))
        UserDefaults.standard.set(recentQueries, forKey: "search.recents")
    }
}
