import Foundation
import Observation

@Observable
final class AppModel {
    var selectedProviderID: String
    var selectedTab: AppTab = .home

    let providers: [any CatalogProvider] = [
        SerienStreamProvider(),
        AniWorldProvider(),
    ]

    init() {
        selectedProviderID = SerienStreamProvider().id
    }

    var activeProvider: any CatalogProvider {
        providers.first(where: { $0.id == selectedProviderID }) ?? providers[0]
    }

    func selectProvider(id: String) {
        guard providers.contains(where: { $0.id == id }) else { return }
        selectedProviderID = id
        selectedTab = .home
    }
}

enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case home
    case search
    case providers

    var id: String { rawValue }
}
