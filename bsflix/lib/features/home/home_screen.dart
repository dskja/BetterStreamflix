import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/pulse_theme.dart';
import '../../core/widgets/pulse_widgets.dart';
import '../../data/models/models.dart';
import '../../data/repositories/providers.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _chip = 0;

  @override
  Widget build(BuildContext context) {
    final feed = ref.watch(homeFeedProvider);
    final provider = ref.watch(activeProviderProvider);
    final continueWatching = feed.rows.isNotEmpty ? feed.rows.first.items : <CatalogItem>[];

    return PulseAtmosphere(
      child: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          // Brand-first chrome matching Pulse mock
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 54, 20, 8),
              child: Row(
                children: [
                  Container(
                    width: 46,
                    height: 46,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: PulseColors.ctaGradient,
                      boxShadow: [
                        BoxShadow(
                          color: PulseColors.amber.withValues(alpha: 0.35),
                          blurRadius: 14,
                          offset: const Offset(0, 6),
                        ),
                      ],
                    ),
                    child: Text(
                      provider.name.substring(0, 1).toUpperCase(),
                      style: const TextStyle(
                        color: PulseColors.onCta,
                        fontWeight: FontWeight.w800,
                        fontSize: 18,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Hello',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const PulseBrandMark(compact: true),
                      ],
                    ),
                  ),
                  _RoundIconButton(
                    icon: Icons.search_rounded,
                    onTap: () => context.go('/search'),
                  ),
                  const SizedBox(width: 8),
                  _RoundIconButton(
                    icon: Icons.tune_rounded,
                    onTap: () => context.go('/providers'),
                  ),
                ],
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 48,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
                children: [
                  PulseChip(
                    label: 'Movies',
                    selected: _chip == 0,
                    onTap: () => setState(() => _chip = 0),
                  ),
                  const SizedBox(width: 10),
                  PulseChip(
                    label: 'TV Shows',
                    selected: _chip == 1,
                    onTap: () => setState(() => _chip = 1),
                  ),
                  const SizedBox(width: 10),
                  PulseChip(
                    label: provider.name,
                    selected: _chip == 2,
                    onTap: () => context.go('/providers'),
                  ),
                ],
              ),
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 18)),
          SliverToBoxAdapter(
            child: PulseHeroCard(
              title: feed.featured.title,
              subtitle:
                  '${feed.featured.year} · ${feed.featured.genres.take(2).join(' · ')}',
              imageUrl: feed.featured.bannerUrl,
              ctaLabel: 'Watch Now',
              onCta: () => context.push('/play/${feed.featured.id}'),
              onOpen: () => context.push('/title/${feed.featured.id}'),
            ),
          ),
          SliverToBoxAdapter(
            child: PulseSectionHeader(
              title: 'Continue Watching',
              trailing: Text(
                'See all',
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: PulseColors.amberBright,
                    ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 230,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                scrollDirection: Axis.horizontal,
                itemCount: continueWatching.length,
                separatorBuilder: (_, __) => const SizedBox(width: 14),
                itemBuilder: (context, index) {
                  final item = continueWatching[index];
                  return PulsePosterCard(
                    title: item.title,
                    imageUrl: item.posterUrl,
                    progress: 0.25 + (index * 0.18) % 0.7,
                    onTap: () => context.push('/title/${item.id}'),
                  );
                },
              ),
            ),
          ),
          ...feed.rows.skip(1).expand((row) {
            return [
              SliverToBoxAdapter(child: PulseSectionHeader(title: row.title)),
              SliverToBoxAdapter(
                child: SizedBox(
                  height: 230,
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
          const SliverToBoxAdapter(child: SizedBox(height: 120)),
        ],
      ),
    );
  }
}

class _RoundIconButton extends StatelessWidget {
  const _RoundIconButton({required this.icon, required this.onTap});

  final IconData icon;
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
        child: SizedBox(
          width: 42,
          height: 42,
          child: Icon(icon, color: PulseColors.mist, size: 20),
        ),
      ),
    );
  }
}
