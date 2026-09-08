import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/pulse_theme.dart';
import '../../core/widgets/pulse_widgets.dart';
import '../../data/repositories/providers.dart';

class DetailScreen extends ConsumerWidget {
  const DetailScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final item = ref.watch(catalogItemProvider(id));
    if (item == null) {
      return const Scaffold(
        backgroundColor: PulseColors.ink,
        body: Center(child: Text('Title not found')),
      );
    }

    return Scaffold(
      backgroundColor: PulseColors.ink,
      body: PulseAtmosphere(
        child: CustomScrollView(
          physics: const BouncingScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: SafeArea(
                bottom: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                  child: Row(
                    children: [
                      _CircleBack(onTap: () => context.pop()),
                      const Spacer(),
                      const PulseBrandMark(compact: true),
                      const Spacer(),
                      const SizedBox(width: 42),
                    ],
                  ),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Positioned(
                      left: 28,
                      right: 28,
                      bottom: -12,
                      height: 52,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: RadialGradient(
                            colors: [
                              PulseColors.amber.withValues(alpha: 0.48),
                              Colors.transparent,
                            ],
                          ),
                        ),
                      ),
                    ),
                    AspectRatio(
                      aspectRatio: 16 / 10,
                      child: Container(
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(28),
                          border: Border.all(color: PulseColors.hairlineStrong),
                          boxShadow: [
                            BoxShadow(
                              color: PulseColors.amber.withValues(alpha: 0.28),
                              blurRadius: 28,
                              offset: const Offset(0, 14),
                            ),
                          ],
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            PulseNetworkImage(url: item.bannerUrl),
                            Container(color: Colors.black26),
                            Center(
                              child: GestureDetector(
                                onTap: () => context.push('/play/${item.id}'),
                                child: Container(
                                  width: 72,
                                  height: 72,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    gradient: PulseColors.ctaGradient,
                                    boxShadow: [
                                      BoxShadow(
                                        color: PulseColors.amber
                                            .withValues(alpha: 0.45),
                                        blurRadius: 20,
                                      ),
                                    ],
                                  ),
                                  child: const Icon(
                                    Icons.play_arrow_rounded,
                                    size: 40,
                                    color: PulseColors.onCta,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 28, 20, 120),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${item.year}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      item.title,
                      style: Theme.of(context).textTheme.displayMedium,
                    ),
                    const SizedBox(height: 14),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        ...item.genres.map(_MetaPill.new),
                        _MetaPill(item.kind.name.toUpperCase()),
                        if (item.rating != null)
                          _MetaPill('★ ${item.rating!.toStringAsFixed(1)}'),
                      ],
                    ),
                    const SizedBox(height: 22),
                    PulseCtaButton(
                      label: 'Watch Now',
                      expand: true,
                      onPressed: () => context.push('/play/${item.id}'),
                    ),
                    const SizedBox(height: 28),
                    Text(
                      'Synopsis',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      item.overview,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MetaPill extends StatelessWidget {
  const _MetaPill(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color: PulseColors.inkPanel,
        borderRadius: BorderRadius.circular(50),
        border: Border.all(color: PulseColors.hairline),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: PulseColors.mist,
            ),
      ),
    );
  }
}

class _CircleBack extends StatelessWidget {
  const _CircleBack({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: PulseColors.inkPanel,
      shape: const CircleBorder(
        side: BorderSide(color: PulseColors.hairline),
      ),
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: onTap,
        child: const SizedBox(
          width: 42,
          height: 42,
          child: Icon(Icons.arrow_back_rounded, color: PulseColors.mist),
        ),
      ),
    );
  }
}
