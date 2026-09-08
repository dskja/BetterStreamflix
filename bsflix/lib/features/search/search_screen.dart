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
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 12, 20, 6),
              child: PulseBrandMark(),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(
                'Search',
                style: Theme.of(context).textTheme.displayMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
              child: TextField(
                onChanged: (v) =>
                    ref.read(searchQueryProvider.notifier).state = v,
                style: const TextStyle(color: PulseColors.mist),
                cursorColor: PulseColors.amberBright,
                decoration: InputDecoration(
                  hintText: 'Titles, genres…',
                  hintStyle: const TextStyle(color: PulseColors.mistFaint),
                  prefixIcon: const Icon(
                    Icons.search_rounded,
                    color: PulseColors.mistFaint,
                  ),
                  filled: true,
                  fillColor: PulseColors.inkPanel.withValues(alpha: 0.9),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 18,
                    vertical: 16,
                  ),
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
                      color: PulseColors.amberBright.withValues(alpha: 0.6),
                    ),
                  ),
                ),
              ),
            ),
            if (query.isEmpty)
              Expanded(
                child: Center(
                  child: Text(
                    'Try “Dune”, “Fallout”, or “Drama”.',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              )
            else if (results.isEmpty)
              Expanded(
                child: Center(
                  child: Text(
                    'No results',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              )
            else
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 120),
                  itemCount: results.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final item = results[index];
                    return PulseGlassPanel(
                      radius: 22,
                      onTap: () => context.push('/title/${item.id}'),
                      child: Row(
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(14),
                            child: SizedBox(
                              width: 58,
                              height: 84,
                              child: PulseNetworkImage(url: item.posterUrl),
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.title,
                                  style:
                                      Theme.of(context).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  '${item.year} · ${item.genres.take(2).join(' · ')}',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ),
                          const Icon(
                            Icons.chevron_right_rounded,
                            color: PulseColors.mistFaint,
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
