import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/pulse_theme.dart';
import '../../core/widgets/pulse_widgets.dart';
import '../../data/repositories/providers.dart';

class SearchScreen extends ConsumerWidget {
  const SearchScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final query = ref.watch(searchQueryProvider);
    final results = ref.watch(searchResultsProvider);

    return PulseAtmosphere(
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
              child: Text(
                'BetterStreamflix',
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: PulseColors.amberBright,
                      fontWeight: FontWeight.w700,
                    ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text('Search', style: Theme.of(context).textTheme.headlineMedium),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 10),
              child: TextField(
                onChanged: (v) =>
                    ref.read(searchQueryProvider.notifier).state = v,
                style: const TextStyle(color: PulseColors.mist),
                decoration: const InputDecoration(
                  hintText: 'Movies, series, genres…',
                  prefixIcon: Icon(Icons.search, color: PulseColors.mistFaint),
                ),
              ),
            ),
            if (query.isEmpty)
              const Expanded(
                child: Center(
                  child: Text(
                    'Try “Dune”, “Fallout”, or “Drama”.',
                    style: TextStyle(color: PulseColors.mistFaint),
                  ),
                ),
              )
            else if (results.isEmpty)
              const Expanded(
                child: Center(
                  child: Text(
                    'No results',
                    style: TextStyle(color: PulseColors.mistDim),
                  ),
                ),
              )
            else
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 110),
                  itemCount: results.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final item = results[index];
                    return PulseGlassPanel(
                      onTap: () => context.push('/title/${item.id}'),
                      child: Row(
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(14),
                            child: Image.network(
                              item.posterUrl,
                              width: 56,
                              height: 80,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(
                                width: 56,
                                height: 80,
                                color: PulseColors.inkSoft,
                              ),
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.title,
                                  style: Theme.of(context).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  '${item.year} · ${item.kind.name}',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}
