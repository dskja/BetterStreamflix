import Foundation
import Observation

@Observable
final class AppModel {
    var selectedProviderID: String
    var selectedTab: AppTab = .home
    let library = LibraryStore.shared

    let providers: [any CatalogProvider] = [
        TMDbProvider(),
        SerienStreamProvider(),
        AniWorldProvider(),
        FilmPalastProvider(),
    ]

    init() {
        let saved = UserDefaults.standard.string(forKey: "activeProviderID")
        if let saved, providers.contains(where: { $0.id == saved }) {
            selectedProviderID = saved
        } else if AppSecrets.hasTMDbKey {
            selectedProviderID = TMDbProvider().id
        } else {
            selectedProviderID = SerienStreamProvider().id
        }
    }

    var activeProvider: any CatalogProvider {
        providers.first(where: { $0.id == selectedProviderID }) ?? providers[0]
    }

    func selectProvider(id: String) {
        guard providers.contains(where: { $0.id == id }) else { return }
        selectedProviderID = id
        UserDefaults.standard.set(id, forKey: "activeProviderID")
        selectedTab = .home
    }

    func provider(forHint hint: String?) -> (any CatalogProvider)? {
        guard let hint else { return nil }
        return providers.first { $0.id == hint }
    }
}

enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case home
    case search
    case library
    case providers
    case settings

    var id: String { rawValue }
}
