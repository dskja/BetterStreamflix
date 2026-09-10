import SwiftUI

struct ProvidersView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        List {
            Section {
                ForEach(app.providers, id: \.id) { provider in
                    Button {
                        app.selectProvider(id: provider.id)
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: iconName(for: provider.id))
                                .font(.title3)
                                .foregroundStyle(EmberTheme.accent)
                                .frame(width: 28)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(provider.name)
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                Text(provider.baseURL.host() ?? provider.baseURL.absoluteString)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if provider.id == app.activeProvider.id {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(EmberTheme.accent)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(.ultraThinMaterial)
                            .padding(.vertical, 2)
                    )
                }
            } header: {
                Text("Catalog sources")
            } footer: {
                Text("Beta v1 ships SerienStream and AniWorld. More providers land after the Liquid Glass shell is solid.")
            }
        }
        .scrollContentBackground(.hidden)
        .emberBackground()
        .navigationTitle("Providers")
    }

    private func iconName(for id: String) -> String {
        switch id {
        case "aniworld":
            return "sparkles.tv"
        default:
            return "tv"
        }
    }
}
