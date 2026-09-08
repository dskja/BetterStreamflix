import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Pulse — charcoal + amber→orange energy (Peacock-inspired consumer streaming).
abstract final class PulseColors {
  static const ink = Color(0xFF07070A);
  static const inkElevated = Color(0xFF0E0E14);
  static const inkPanel = Color(0xFF16161F);
  static const inkSoft = Color(0xFF22222E);
  static const inkGlass = Color(0xE6121218);
  static const amber = Color(0xFFFF8A00);
  static const amberBright = Color(0xFFFFD60A);
  static const amberMuted = Color(0xFFFF6B00);
  static const mist = Color(0xFFF5F5F7);
  static const mistDim = Color(0xFFA1A1AA);
  static const mistFaint = Color(0xFF6B6B76);
  static const hairline = Color(0x28FFFFFF);
  static const hairlineStrong = Color(0x40FFFFFF);
  static const danger = Color(0xFFFF453A);
  static const onCta = Color(0xFF141000);

  static const ctaGradient = LinearGradient(
    begin: Alignment.centerLeft,
    end: Alignment.centerRight,
    colors: [amberBright, amber, amberMuted],
  );

  static const atmosphere = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [
      Color(0x332B1A00),
      Color(0x140B0B0F),
      ink,
    ],
    stops: [0.0, 0.35, 1.0],
  );
}

abstract final class PulseTheme {
  static ThemeData dark() {
    final display = GoogleFonts.spaceGroteskTextTheme();
    final body = GoogleFonts.manropeTextTheme();

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: PulseColors.ink,
      splashFactory: InkSparkle.splashFactory,
      colorScheme: const ColorScheme.dark(
        surface: PulseColors.inkElevated,
        primary: PulseColors.amber,
        secondary: PulseColors.amberBright,
        onPrimary: PulseColors.onCta,
        onSurface: PulseColors.mist,
        error: PulseColors.danger,
      ),
      textTheme: TextTheme(
        displayLarge: display.displayLarge?.copyWith(
          fontSize: 40,
          height: 1.05,
          fontWeight: FontWeight.w700,
          letterSpacing: -1.4,
          color: PulseColors.mist,
        ),
        displayMedium: display.displayMedium?.copyWith(
          fontSize: 32,
          height: 1.08,
          fontWeight: FontWeight.w700,
          letterSpacing: -1.0,
          color: PulseColors.mist,
        ),
        headlineMedium: display.headlineMedium?.copyWith(
          fontSize: 24,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.4,
          color: PulseColors.mist,
        ),
        titleLarge: display.titleLarge?.copyWith(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: PulseColors.mist,
        ),
        titleMedium: body.titleMedium?.copyWith(
          fontSize: 16,
          fontWeight: FontWeight.w600,
          color: PulseColors.mist,
        ),
        bodyLarge: body.bodyLarge?.copyWith(
          fontSize: 16,
          height: 1.45,
          color: PulseColors.mistDim,
        ),
        bodyMedium: body.bodyMedium?.copyWith(
          fontSize: 14,
          height: 1.4,
          color: PulseColors.mistDim,
        ),
        bodySmall: body.bodySmall?.copyWith(
          fontSize: 12,
          color: PulseColors.mistFaint,
        ),
        labelLarge: body.labelLarge?.copyWith(
          fontSize: 14,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.2,
          color: PulseColors.mist,
        ),
        labelMedium: body.labelMedium?.copyWith(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.3,
          color: PulseColors.mistDim,
        ),
        labelSmall: body.labelSmall?.copyWith(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          letterSpacing: 1.2,
          color: PulseColors.amberBright,
        ),
      ),
      dividerColor: PulseColors.hairline,
    );
  }
}
