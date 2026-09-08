import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/models.dart';
import 'catalog_repository.dart';

final catalogRepositoryProvider = Provider<CatalogRepository>((ref) {
  return CatalogRepository();
});

final activeProviderProvider = Provider<StreamProviderInfo>((ref) {
  ref.watch(providersRevisionProvider);
  return ref.watch(catalogRepositoryProvider).activeProvider;
});

final providersRevisionProvider = StateProvider<int>((ref) => 0);

final homeFeedProvider = Provider<HomeFeed>((ref) {
  ref.watch(providersRevisionProvider);
  return ref.watch(catalogRepositoryProvider).home();
});

final providersListProvider =
    Provider.family<List<StreamProviderInfo>, ({String? language, String query})>(
  (ref, args) {
    ref.watch(providersRevisionProvider);
    return ref
        .watch(catalogRepositoryProvider)
        .providers(language: args.language, query: args.query);
  },
);

final searchQueryProvider = StateProvider<String>((ref) => '');

final searchResultsProvider = Provider<List<CatalogItem>>((ref) {
  final q = ref.watch(searchQueryProvider);
  ref.watch(providersRevisionProvider);
  return ref.watch(catalogRepositoryProvider).search(q);
});

final catalogItemProvider = Provider.family<CatalogItem?, String>((ref, id) {
  ref.watch(providersRevisionProvider);
  return ref.watch(catalogRepositoryProvider).byId(id);
});
