import SwiftUI

struct SearchView: View {
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var results: [MediaItem] = []
    @State private var isSearching = false
    @State private var errorMessage: String?
    @State private var searchTask: Task<Void, Never>?

    var body: some View {
        List {
            if isSearching {
                HStack {
                    ProgressView()
                    Text("Searching…")
                        .foregroundStyle(.secondary)
                }
                .listRowBackground(Color.clear)
            } else if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .listRowBackground(Color.clear)
            } else if results.isEmpty && !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                ContentUnavailableView.search(text: query)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(results) { item in
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
            Task { await runSearch(query) }
        }
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
            isSearching = false
        } catch {
            errorMessage = error.localizedDescription
            results = []
            isSearching = false
        }
    }
}
