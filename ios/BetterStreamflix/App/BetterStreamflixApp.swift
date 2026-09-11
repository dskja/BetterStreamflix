import SwiftUI

@main
struct BetterStreamflixApp: App {
    @State private var appModel = AppModel()

    init() {
        // Match Android BetterStreamflixApp → DnsResolver.setDnsUrl before any network I/O.
        DohResolver.configureAppDNS()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appModel)
                .preferredColorScheme(.dark)
        }
    }
}
