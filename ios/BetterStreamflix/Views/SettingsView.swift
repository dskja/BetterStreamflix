import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var tmdbKey: String = ""
    @State private var savedBanner = false

    var body: some View {
        Form {
            Section {
                SecureField("TMDb API key", text: $tmdbKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Save key override") {
                    AppSecrets.setTMDbOverride(tmdbKey)
                    savedBanner = true
                }
                Button("Clear override (use build key)", role: .destructive) {
                    AppSecrets.setTMDbOverride(nil)
                    tmdbKey = ""
                }
            } header: {
                Text("TMDb")
            } footer: {
                Text(AppSecrets.hasTMDbKey
                     ? "A TMDb key is active (override or build-time secret)."
                     : "No TMDb key found. Paste the same key used by Android BetterStreamflix.")
            }

            Section("About") {
                LabeledContent("App", value: "BetterStreamflix iOS")
                LabeledContent("Version", value: "2.0.0-beta")
                LabeledContent("Active provider", value: app.activeProvider.name)
            }
        }
        .scrollContentBackground(.hidden)
        .emberBackground()
        .navigationTitle("Settings")
        .onAppear {
            tmdbKey = UserDefaults.standard.string(forKey: "TMDB_API_KEY") ?? ""
        }
        .alert("Saved", isPresented: $savedBanner) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("TMDb key override updated.")
        }
    }
}
