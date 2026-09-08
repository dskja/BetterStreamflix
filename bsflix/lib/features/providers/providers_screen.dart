import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/pulse_theme.dart';
import '../../core/widgets/pulse_widgets.dart';
import '../../data/repositories/providers.dart';

class ProvidersScreen extends ConsumerStatefulWidget {
  const ProvidersScreen({super.key});

  @override
  ConsumerState<ProvidersScreen> createState() => _ProvidersScreenState();
}

class _ProvidersScreenState extends ConsumerState<ProvidersScreen> {
  String? _language;
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final providers = ref.watch(
      providersListProvider((language: _language, query: _query)),
    );
    final active = ref.watch(activeProviderProvider);
    final langs = <String?>[null, 'de', 'en', 'es', 'fr', 'it'];

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
                'Providers',
                style: Theme.of(context).textTheme.displayMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 6, 20, 12),
              child: Text(
                'Pick a source. Long-press to favorite.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                onChanged: (v) => setState(() => _query = v),
                style: const TextStyle(color: PulseColors.mist),
                cursorColor: PulseColors.amberBright,
                decoration: InputDecoration(
                  hintText: 'Search providers',
                  hintStyle: const TextStyle(color: PulseColors.mistFaint),
                  prefixIcon: const Icon(
                    Icons.search_rounded,
                    color: PulseColors.mistFaint,
                  ),
                  filled: true,
                  fillColor: PulseColors.inkPanel.withValues(alpha: 0.9),
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
            const SizedBox(height: 14),
            SizedBox(
              height: 44,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                scrollDirection: Axis.horizontal,
                itemCount: langs.length,
                separatorBuilder: (_, __) => const SizedBox(width: 10),
                itemBuilder: (context, index) {
                  final code = langs[index];
                  return PulseChip(
                    label: code?.toUpperCase() ?? 'ALL',
                    selected: _language == code,
                    onTap: () => setState(() => _language = code),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: ListView.separated(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 120),
                itemCount: providers.length,
                separatorBuilder: (_, __) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  final p = providers[index];
                  final selected = p.id == active.id;
                  return PulseGlassPanel(
                    selected: selected,
                    radius: 24,
                    onTap: () {
                      ref.read(catalogRepositoryProvider).selectProvider(p.id);
                      ref.read(providersRevisionProvider.notifier).state++;
                    },
                    onLongPress: () {
                      ref.read(catalogRepositoryProvider).toggleFavorite(p.id);
                      ref.read(providersRevisionProvider.notifier).state++;
                    },
                    child: Row(
                      children: [
                        Container(
                          width: 52,
                          height: 52,
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            gradient: PulseColors.ctaGradient,
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: Text(
                            p.name.substring(0, 1).toUpperCase(),
                            style: const TextStyle(
                              color: PulseColors.onCta,
                              fontWeight: FontWeight.w800,
                              fontSize: 20,
                            ),
                          ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Flexible(
                                    child: Text(
                                      p.name,
                                      style: Theme.of(context)
                                          .textTheme
                                          .titleMedium,
                                    ),
                                  ),
                                  if (p.favorite) ...[
                                    const SizedBox(width: 6),
                                    const Icon(
                                      Icons.star_rounded,
                                      size: 18,
                                      color: PulseColors.amberBright,
                                    ),
                                  ],
                                ],
                              ),
                              Text(
                                p.language.toUpperCase(),
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ),
                        ),
                        Text(
                          p.healthy ? 'Online' : 'Offline',
                          style: TextStyle(
                            color: p.healthy
                                ? PulseColors.amberBright
                                : PulseColors.danger,
                            fontWeight: FontWeight.w700,
                            fontSize: 12,
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
