import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../theme/pulse_theme.dart';

class PulseAtmosphere extends StatelessWidget {
  const PulseAtmosphere({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        const ColoredBox(color: PulseColors.ink),
        const DecoratedBox(decoration: BoxDecoration(gradient: PulseColors.atmosphere)),
        Positioned(
          top: -40,
          right: -60,
          child: IgnorePointer(
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    PulseColors.amberBright.withValues(alpha: 0.12),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),
        ),
        child,
      ],
    );
  }
}

class PulseNetworkImage extends StatelessWidget {
  const PulseNetworkImage({
    super.key,
    required this.url,
    this.fit = BoxFit.cover,
  });

  final String url;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    return CachedNetworkImage(
      imageUrl: url,
      fit: fit,
      fadeInDuration: const Duration(milliseconds: 280),
      placeholder: (_, __) => const ColoredBox(color: PulseColors.inkSoft),
      errorWidget: (_, __, ___) => const ColoredBox(
        color: PulseColors.inkSoft,
        child: Center(
          child: Icon(Icons.movie_filter_rounded, color: PulseColors.mistFaint),
        ),
      ),
    );
  }
}

class PulseCtaButton extends StatefulWidget {
  const PulseCtaButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.expand = false,
    this.icon = Icons.play_arrow_rounded,
  });

  final String label;
  final VoidCallback onPressed;
  final bool expand;
  final IconData? icon;

  @override
  State<PulseCtaButton> createState() => _PulseCtaButtonState();
}

class _PulseCtaButtonState extends State<PulseCtaButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final button = GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: widget.onPressed,
      child: AnimatedScale(
        scale: _pressed ? 0.97 : 1,
        duration: const Duration(milliseconds: 120),
        child: Container(
          height: 54,
          padding: const EdgeInsets.symmetric(horizontal: 22),
          decoration: BoxDecoration(
            gradient: PulseColors.ctaGradient,
            borderRadius: BorderRadius.circular(28),
            boxShadow: [
              BoxShadow(
                color: PulseColors.amber.withValues(alpha: 0.35),
                blurRadius: 18,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: widget.expand ? MainAxisSize.max : MainAxisSize.min,
            children: [
              if (widget.icon != null) ...[
                Icon(widget.icon, color: PulseColors.onCta, size: 22),
                const SizedBox(width: 8),
              ],
              Text(
                widget.label,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: PulseColors.onCta,
                      fontWeight: FontWeight.w800,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
    return widget.expand
        ? SizedBox(width: double.infinity, child: button)
        : button;
  }
}

class PulseGhostButton extends StatelessWidget {
  const PulseGhostButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
  });

  final String label;
  final VoidCallback onPressed;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: PulseColors.inkPanel.withValues(alpha: 0.72),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(28),
        side: const BorderSide(color: PulseColors.hairlineStrong),
      ),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(28),
        child: SizedBox(
          height: 54,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (icon != null) ...[
                  Icon(icon, size: 18, color: PulseColors.mist),
                  const SizedBox(width: 8),
                ],
                Text(label, style: Theme.of(context).textTheme.labelLarge),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class PulseChip extends StatelessWidget {
  const PulseChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 11),
        decoration: BoxDecoration(
          gradient: selected ? PulseColors.ctaGradient : null,
          color: selected ? null : PulseColors.inkPanel.withValues(alpha: 0.8),
          borderRadius: BorderRadius.circular(50),
          border: Border.all(
            color: selected ? Colors.transparent : PulseColors.hairline,
          ),
        ),
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: selected ? PulseColors.onCta : PulseColors.mist,
                fontWeight: FontWeight.w700,
              ),
        ),
      ),
    );
  }
}

class PulseGlassPanel extends StatelessWidget {
  const PulseGlassPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 24,
    this.selected = false,
    this.onTap,
    this.onLongPress,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;
  final bool selected;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final panel = AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      padding: padding,
      decoration: BoxDecoration(
        color: selected
            ? PulseColors.amber.withValues(alpha: 0.14)
            : PulseColors.inkPanel.withValues(alpha: 0.88),
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(
          color: selected
              ? PulseColors.amberBright.withValues(alpha: 0.55)
              : PulseColors.hairline,
        ),
      ),
      child: child,
    );
    if (onTap == null && onLongPress == null) return panel;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        borderRadius: BorderRadius.circular(radius),
        child: panel,
      ),
    );
  }
}

/// Peacock-style featured hero — large radius, under-glow, gradient Watch CTA.
class PulseHeroCard extends StatelessWidget {
  const PulseHeroCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.imageUrl,
    required this.ctaLabel,
    required this.onCta,
    this.onOpen,
  });

  final String title;
  final String subtitle;
  final String imageUrl;
  final String ctaLabel;
  final VoidCallback onCta;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: 1),
        duration: const Duration(milliseconds: 620),
        curve: Curves.easeOutCubic,
        builder: (context, t, child) {
          return Opacity(
            opacity: t,
            child: Transform.translate(
              offset: Offset(0, (1 - t) * 18),
              child: child,
            ),
          );
        },
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              left: 24,
              right: 24,
              bottom: -14,
              height: 56,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    colors: [
                      PulseColors.amber.withValues(alpha: 0.55),
                      PulseColors.amberMuted.withValues(alpha: 0.16),
                      Colors.transparent,
                    ],
                  ),
                ),
              ),
            ),
            Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: onOpen,
                borderRadius: BorderRadius.circular(32),
                child: Ink(
                  height: 448,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(32),
                    border: Border.all(color: PulseColors.hairlineStrong),
                    boxShadow: [
                      BoxShadow(
                        color: PulseColors.amber.withValues(alpha: 0.28),
                        blurRadius: 32,
                        offset: const Offset(0, 16),
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(32),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        PulseNetworkImage(url: imageUrl),
                        const DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                Color(0x0007070A),
                                Color(0x0007070A),
                                Color(0x5907070A),
                                Color(0xCC07070A),
                                Color(0xF207070A),
                              ],
                              stops: [0.0, 0.35, 0.55, 0.78, 1.0],
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(22, 22, 22, 22),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: [
                              Text(
                                'FEATURED',
                                style: Theme.of(context).textTheme.labelSmall,
                              ),
                              const SizedBox(height: 10),
                              Text(
                                title,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style:
                                    Theme.of(context).textTheme.displayMedium,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                subtitle,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                              const SizedBox(height: 18),
                              PulseCtaButton(
                                label: ctaLabel,
                                onPressed: onCta,
                                expand: true,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PulsePosterCard extends StatefulWidget {
  const PulsePosterCard({
    super.key,
    required this.title,
    required this.imageUrl,
    required this.onTap,
    this.progress,
  });

  final String title;
  final String imageUrl;
  final VoidCallback onTap;
  final double? progress;

  @override
  State<PulsePosterCard> createState() => _PulsePosterCardState();
}

class _PulsePosterCardState extends State<PulsePosterCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 124,
      child: GestureDetector(
        onTapDown: (_) => setState(() => _pressed = true),
        onTapCancel: () => setState(() => _pressed = false),
        onTapUp: (_) => setState(() => _pressed = false),
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _pressed ? 0.96 : 1,
          duration: const Duration(milliseconds: 120),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              AspectRatio(
                aspectRatio: 2 / 3,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(22),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      PulseNetworkImage(url: widget.imageUrl),
                      const DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [Colors.transparent, Color(0xA607070A)],
                          ),
                        ),
                      ),
                      if (widget.progress != null)
                        Positioned(
                          left: 10,
                          right: 10,
                          bottom: 10,
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: LinearProgressIndicator(
                              value: widget.progress!.clamp(0.05, 1),
                              minHeight: 3,
                              backgroundColor: Colors.white24,
                              color: PulseColors.amber,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Text(
                widget.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: PulseColors.mist,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class PulseSectionHeader extends StatelessWidget {
  const PulseSectionHeader({
    super.key,
    required this.title,
    this.trailing,
  });

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 22, 20, 12),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}

class PulseBrandMark extends StatelessWidget {
  const PulseBrandMark({super.key, this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: 'Better',
            style: TextStyle(
              color: PulseColors.mist,
              fontWeight: FontWeight.w700,
              fontSize: compact ? 15 : 18,
              fontFamily: GoogleFonts.spaceGrotesk().fontFamily,
            ),
          ),
          TextSpan(
            text: 'Streamflix',
            style: TextStyle(
              color: PulseColors.amberBright,
              fontWeight: FontWeight.w700,
              fontSize: compact ? 15 : 18,
              fontFamily: GoogleFonts.spaceGrotesk().fontFamily,
            ),
          ),
        ],
      ),
    );
  }
}

/// Floating glass pill nav — selected destination becomes a filled capsule.
class PulseFloatingNav extends StatelessWidget {
  const PulseFloatingNav({
    super.key,
    required this.index,
    required this.onSelect,
  });

  final int index;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    const items = [
      (Icons.home_outlined, Icons.home_rounded, 'Home'),
      (Icons.search, Icons.search, 'Search'),
      (Icons.dns_outlined, Icons.dns_rounded, 'Providers'),
    ];

    return SafeArea(
      minimum: const EdgeInsets.fromLTRB(20, 0, 20, 16),
      child: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: PulseColors.inkGlass,
          borderRadius: BorderRadius.circular(30),
          border: Border.all(color: const Color(0x33FFD60A)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.55),
              blurRadius: 24,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Row(
          children: List.generate(items.length, (i) {
            final selected = i == index;
            final item = items[i];
            return Expanded(
              child: GestureDetector(
                onTap: () => onSelect(i),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOutCubic,
                  height: 48,
                  decoration: BoxDecoration(
                    color: selected ? PulseColors.mist : Colors.transparent,
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        selected ? item.$2 : item.$1,
                        size: 22,
                        color: selected ? PulseColors.ink : PulseColors.mistDim,
                      ),
                      if (selected) ...[
                        const SizedBox(width: 8),
                        Text(
                          item.$3,
                          style: Theme.of(context).textTheme.labelLarge?.copyWith(
                                color: PulseColors.ink,
                                fontWeight: FontWeight.w800,
                              ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}
