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
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 4),
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
              child: Text(
                'Choose provider',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 6, 20, 10),
              child: Text(
                'Same Pulse chrome for every provider surface.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                onChanged: (v) => setState(() => _query = v),
                style: const TextStyle(color: PulseColors.mist),
                decoration: const InputDecoration(
                  hintText: 'Search providers',
                  prefixIcon: Icon(Icons.search, color: PulseColors.mistFaint),
                ),
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 42,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                scrollDirection: Axis.horizontal,
                itemCount: langs.length,
                separatorBuilder: (_, __) => const SizedBox(width: 10),
                itemBuilder: (context, index) {
                  final code = langs[index];
                  final selected = _language == code;
                  final label = code?.toUpperCase() ?? 'ALL';
                  return ChoiceChip(
                    label: Text(label),
                    selected: selected,
                    onSelected: (_) => setState(() => _language = code),
                    selectedColor: PulseColors.amber,
                    backgroundColor: PulseColors.inkPanel,
                    labelStyle: TextStyle(
                      color: selected
                          ? const Color(0xFF1A1200)
                          : PulseColors.mist,
                      fontWeight: FontWeight.w600,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(50),
                      side: BorderSide(
                        color: selected
                            ? Colors.transparent
                            : PulseColors.hairline,
                      ),
                    ),
                  );
                },
              ),
            ),
            const PulseSectionHeader(title: 'Long-press to favorite'),
            Expanded(
              child: ListView.separated(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 110),
                itemCount: providers.length,
                separatorBuilder: (_, __) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  final p = providers[index];
                  final selected = p.id == active.id;
                  return PulseGlassPanel(
                    selected: selected,
                    onTap: () {
                      ref.read(catalogRepositoryProvider).selectProvider(p.id);
                      ref.read(providersRevisionProvider.notifier).state++;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Switched to ${p.name}'),
                          backgroundColor: PulseColors.inkPanel,
                        ),
                      );
                    },
                    child: GestureDetector(
                      onLongPress: () {
                        ref
                            .read(catalogRepositoryProvider)
                            .toggleFavorite(p.id);
                        ref.read(providersRevisionProvider.notifier).state++;
                      },
                      child: Row(
                        children: [
                          Container(
                            width: 48,
                            height: 48,
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              gradient: PulseColors.ctaGradient,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Text(
                              p.name.substring(0, 1).toUpperCase(),
                              style: const TextStyle(
                                color: Color(0xFF1A1200),
                                fontWeight: FontWeight.w700,
                                fontSize: 18,
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          if (p.favorite)
                            const Padding(
                              padding: EdgeInsets.only(right: 8),
                              child: Icon(
                                Icons.star_rounded,
                                color: PulseColors.amberBright,
                                size: 20,
                              ),
                            ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  p.name,
                                  style:
                                      Theme.of(context).textTheme.titleMedium,
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
                              fontWeight: FontWeight.w600,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
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
