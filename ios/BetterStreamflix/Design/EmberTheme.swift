import SwiftUI

enum EmberTheme {
    static let background = Color(red: 0.03, green: 0.035, blue: 0.045)
    static let canvas = background
    static let canvasSoft = Color(red: 0.055, green: 0.065, blue: 0.09)
    static let surface = Color(red: 0.09, green: 0.10, blue: 0.14)
    static let surfaceElevated = Color(red: 0.12, green: 0.13, blue: 0.18)
    static let ink = Color(red: 0.96, green: 0.97, blue: 0.98)
    static let inkSoft = Color(red: 0.66, green: 0.69, blue: 0.74)
    static let mist = Color(red: 0.42, green: 0.45, blue: 0.50)
    static let accent = Color(red: 0.90, green: 0.04, blue: 0.08)
    static let accentLight = Color(red: 1.0, green: 0.24, blue: 0.27)
    static let gold = Color(red: 1.0, green: 0.69, blue: 0.25)

    static let spaceSM: CGFloat = 8
    static let spaceMD: CGFloat = 16
    static let spaceLG: CGFloat = 24
    static let spaceXL: CGFloat = 32
    static let radiusMD: CGFloat = 16
    static let radiusLG: CGFloat = 24
}

struct GlassCardModifier: ViewModifier {
    var cornerRadius: CGFloat = 22

    func body(content: Content) -> some View {
        content
            .glassEffect(.regular.interactive(), in: .rect(cornerRadius: cornerRadius))
    }
}

struct GlassChipModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .glassEffect(.regular.interactive(), in: .capsule)
    }
}

extension View {
    func glassCard(cornerRadius: CGFloat = 22) -> some View {
        modifier(GlassCardModifier(cornerRadius: cornerRadius))
    }

    func glassChrome(cornerRadius: CGFloat) -> some View {
        glassCard(cornerRadius: cornerRadius)
    }

    func glassChip() -> some View {
        modifier(GlassChipModifier())
    }

    func emberBackground() -> some View {
        background {
            ZStack {
                EmberTheme.background.ignoresSafeArea()
                RadialGradient(
                    colors: [EmberTheme.accent.opacity(0.18), .clear],
                    center: .topTrailing,
                    startRadius: 20,
                    endRadius: 420
                )
                .ignoresSafeArea()
            }
        }
    }
}
