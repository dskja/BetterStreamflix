import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/pulse_theme.dart';
import '../../core/widgets/pulse_widgets.dart';
import '../../data/repositories/providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feed = ref.watch(homeFeedProvider);
    final provider = ref.watch(activeProviderProvider);

    return PulseAtmosphere(
      child: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 52, 20, 14),
              child: Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: PulseColors.inkSoft,
                      shape: BoxShape.circle,
                      border: Border.all(color: PulseColors.hairline),
                    ),
                    child: Text(
                      provider.name.substring(0, 1).toUpperCase(),
                      style: const TextStyle(
                        color: PulseColors.amberBright,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'BetterStreamflix',
                          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                                color: PulseColors.amberBright,
                                fontWeight: FontWeight.w700,
                              ),
                        ),
                        Text(
                          'Watching via ${provider.name}',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: () => context.go('/providers'),
                    icon: const Icon(Icons.tune_rounded, color: PulseColors.mist),
                  ),
                ],
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: PulseHeroCard(
              title: feed.featured.title,
              subtitle: '${feed.featured.year} · ${feed.featured.genres.join(' · ')}',
              imageUrl: feed.featured.bannerUrl,
              ctaLabel: 'Watch Now',
              onCta: () => context.push('/play/${feed.featured.id}'),
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 22)),
          ...feed.rows.expand((row) {
            return [
              SliverToBoxAdapter(child: PulseSectionHeader(title: row.title)),
              SliverToBoxAdapter(
                child: SizedBox(
                  height: 210,
                  child: ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    scrollDirection: Axis.horizontal,
                    itemCount: row.items.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 14),
                    itemBuilder: (context, index) {
                      final item = row.items[index];
                      return PulsePosterCard(
                        title: item.title,
                        imageUrl: item.posterUrl,
                        onTap: () => context.push('/title/${item.id}'),
                      );
                    },
                  ),
                ),
              ),
            ];
          }),
          const SliverToBoxAdapter(child: SizedBox(height: 110)),
        ],
      ),
    );
  }
}
