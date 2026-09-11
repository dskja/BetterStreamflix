import SwiftUI

struct LibraryView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        List {
            Section("Weitersehen") {
                if app.library.continueWatching.isEmpty {
                    Text("Nothing in progress yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(app.library.continueWatching) { entry in
                        NavigationLink {
                            DetailView(item: entry.asMediaItem, forcedProviderID: entry.providerID)
                        } label: {
                            HStack(spacing: 12) {
                                poster(entry.posterURL)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(entry.title).foregroundStyle(.white)
                                    Text(entry.providerID)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                app.library.removeContinue(entry)
                            } label: {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                    }
                }
            }

            Section("Merkliste") {
                if app.library.favorites.isEmpty {
                    Text("Save titles with the bookmark on detail screens.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(app.library.favorites) { item in
                        NavigationLink {
                            DetailView(item: item, forcedProviderID: item.providerHint)
                        } label: {
                            HStack(spacing: 12) {
                                poster(item.posterURL)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.title).foregroundStyle(.white)
                                    Text(item.providerHint ?? "favorite")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .emberBackground()
        .navigationTitle("Library")
    }

    private func poster(_ url: URL?) -> some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                EmberTheme.surfaceElevated
            }
        }
        .frame(width: 44, height: 66)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}
