import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app
        TabView(selection: $app.selectedTab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                NavigationStack { HomeView() }
            }
            Tab("Search", systemImage: "magnifyingglass", value: AppTab.search) {
                NavigationStack { SearchView() }
            }
            Tab("Library", systemImage: "bookmark.fill", value: AppTab.library) {
                NavigationStack { LibraryView() }
            }
            Tab("Providers", systemImage: "antenna.radiowaves.left.and.right", value: AppTab.providers) {
                NavigationStack { ProvidersView() }
            }
            Tab("Settings", systemImage: "gearshape.fill", value: AppTab.settings) {
                NavigationStack { SettingsView() }
            }
        }
        .tint(EmberTheme.accent)
        .preferredColorScheme(.dark)
    }
}
