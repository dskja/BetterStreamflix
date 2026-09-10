import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app
        TabView(selection: $app.selectedTab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                NavigationStack {
                    HomeView()
                }
            }
            Tab("Search", systemImage: "magnifyingglass", value: AppTab.search) {
                NavigationStack {
                    SearchView()
                }
            }
            Tab("Providers", systemImage: "antenna.radiowaves.left.and.right", value: AppTab.providers) {
                NavigationStack {
                    ProvidersView()
                }
            }
        }
        .tint(EmberTheme.accent)
        .preferredColorScheme(.dark)
    }
}
