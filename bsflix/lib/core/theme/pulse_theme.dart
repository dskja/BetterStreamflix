import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Pulse design tokens — charcoal surfaces + amber→orange energy.
abstract final class PulseColors {
  static const ink = Color(0xFF0B0B0F);
  static const inkElevated = Color(0xFF121218);
  static const inkPanel = Color(0xFF1A1A22);
  static const inkSoft = Color(0xFF262632);
  static const amber = Color(0xFFFF8A00);
  static const amberBright = Color(0xFFFFD60A);
  static const amberMuted = Color(0xFFFF6B00);
  static const mist = Color(0xFFFFFFFF);
  static const mistDim = Color(0xFFA1A1AA);
  static const mistFaint = Color(0xFF71717A);
  static const hairline = Color(0x22FFFFFF);
  static const danger = Color(0xFFFF453A);

  static const ctaGradient = LinearGradient(
    colors: [amberBright, amber, amberMuted],
  );
}

abstract final class PulseTheme {
  static ThemeData dark() {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: PulseColors.ink,
      colorScheme: const ColorScheme.dark(
        surface: PulseColors.inkElevated,
        primary: PulseColors.amber,
        secondary: PulseColors.amberBright,
        onPrimary: Color(0xFF1A1200),
        onSurface: PulseColors.mist,
        error: PulseColors.danger,
      ),
    );

    final display = GoogleFonts.spaceGroteskTextTheme(base.textTheme);
    final body = GoogleFonts.manropeTextTheme(base.textTheme);

    return base.copyWith(
      textTheme: display.copyWith(
        displayLarge: display.displayLarge?.copyWith(
          fontWeight: FontWeight.w700,
          letterSpacing: -1.2,
          color: PulseColors.mist,
        ),
        displayMedium: display.displayMedium?.copyWith(
          fontWeight: FontWeight.w700,
          letterSpacing: -0.9,
          color: PulseColors.mist,
        ),
        headlineMedium: display.headlineMedium?.copyWith(
          fontWeight: FontWeight.w700,
          color: PulseColors.mist,
        ),
        titleLarge: display.titleLarge?.copyWith(
          fontWeight: FontWeight.w600,
          color: PulseColors.mist,
        ),
        titleMedium: body.titleMedium?.copyWith(
          fontWeight: FontWeight.w600,
          color: PulseColors.mist,
        ),
        bodyLarge: body.bodyLarge?.copyWith(color: PulseColors.mistDim),
        bodyMedium: body.bodyMedium?.copyWith(color: PulseColors.mistDim),
        labelLarge: body.labelLarge?.copyWith(
          fontWeight: FontWeight.w600,
          color: PulseColors.mist,
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: PulseColors.inkPanel.withValues(alpha: 0.88),
        hintStyle: const TextStyle(color: PulseColors.mistFaint),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(28),
          borderSide: const BorderSide(color: PulseColors.hairline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(28),
          borderSide: const BorderSide(color: PulseColors.hairline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(28),
          borderSide: BorderSide(
            color: PulseColors.amberBright.withValues(alpha: 0.55),
          ),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
      ),
    );
  }
}
