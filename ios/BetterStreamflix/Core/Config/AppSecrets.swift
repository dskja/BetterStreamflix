import Foundation

enum AppSecrets {
    private static let tmdbOverrideKey = "TMDB_API_KEY"

    static var tmdbAPIKey: String {
        if let override = UserDefaults.standard.string(forKey: tmdbOverrideKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !override.isEmpty {
            return override
        }
        return GeneratedSecrets.tmdbAPIKey
    }

    static var hasTMDbKey: Bool {
        !tmdbAPIKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && tmdbAPIKey != "null"
    }

    static func setTMDbOverride(_ value: String?) {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmed.isEmpty {
            UserDefaults.standard.removeObject(forKey: tmdbOverrideKey)
        } else {
            UserDefaults.standard.set(trimmed, forKey: tmdbOverrideKey)
        }
    }
}
