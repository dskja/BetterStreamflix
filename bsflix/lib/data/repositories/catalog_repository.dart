import '../models/models.dart';

/// Demo catalog — replace with live provider/extractor clients later.
class CatalogRepository {
  CatalogRepository();

  static const _img = 'https://image.tmdb.org/t/p';

  final List<StreamProviderInfo> _providers = [
    const StreamProviderInfo(
      id: 'serienstream',
      name: 'SerienStream',
      language: 'de',
      healthy: true,
      favorite: true,
    ),
    const StreamProviderInfo(
      id: 'kinoger',
      name: 'KinoGer',
      language: 'de',
      healthy: true,
    ),
    const StreamProviderInfo(
      id: 'flixhq',
      name: 'FlixHQ',
      language: 'en',
      healthy: true,
    ),
    const StreamProviderInfo(
      id: 'hdfilme',
      name: 'HDFilme',
      language: 'de',
      healthy: false,
    ),
    const StreamProviderInfo(
      id: 'cuevana',
      name: 'Cuevana',
      language: 'es',
      healthy: true,
    ),
  ];

  String _activeProviderId = 'serienstream';

  String get activeProviderId => _activeProviderId;

  StreamProviderInfo get activeProvider =>
      _providers.firstWhere((p) => p.id == _activeProviderId);

  List<StreamProviderInfo> providers({String? language, String query = ''}) {
    return _providers.where((p) {
      final langOk = language == null || p.language == language;
      final q = query.trim().toLowerCase();
      final queryOk = q.isEmpty || p.name.toLowerCase().contains(q);
      return langOk && queryOk;
    }).toList()
      ..sort((a, b) {
        if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
        if (a.healthy != b.healthy) return a.healthy ? -1 : 1;
        return a.name.compareTo(b.name);
      });
  }

  void selectProvider(String id) {
    if (_providers.any((p) => p.id == id)) {
      _activeProviderId = id;
    }
  }

  void toggleFavorite(String id) {
    final i = _providers.indexWhere((p) => p.id == id);
    if (i < 0) return;
    _providers[i] = _providers[i].copyWith(favorite: !_providers[i].favorite);
  }

  List<CatalogItem> get _catalog => [
        CatalogItem(
          id: 'dune',
          title: 'Dune: Part Two',
          kind: MediaKind.movie,
          posterUrl: '$_img/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg',
          bannerUrl: '$_img/w1280/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg',
          overview:
              'Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.',
          year: 2024,
          genres: const ['Sci-Fi', 'Adventure'],
          providerId: _activeProviderId,
          rating: 8.5,
        ),
        CatalogItem(
          id: 'oppenheimer',
          title: 'Oppenheimer',
          kind: MediaKind.movie,
          posterUrl: '$_img/w500/8Gxv8gSFCU0XGDykE0umKHlPlBb.jpg',
          bannerUrl: '$_img/w1280/rLb2cwF3Pazuxaj0sRXQ037tGI1.jpg',
          overview:
              'The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.',
          year: 2023,
          genres: const ['Drama', 'History'],
          providerId: _activeProviderId,
          rating: 8.3,
        ),
        CatalogItem(
          id: 'the-bear',
          title: 'The Bear',
          kind: MediaKind.series,
          posterUrl: '$_img/w500/sHRhRdheJsjga37rL4QY8zEZfH.jpg',
          bannerUrl: '$_img/w1280/9n2tJBplPbgR2ca05FgbakK0pEC.jpg',
          overview:
              'A young chef from the fine dining world returns to Chicago to run his family\'s sandwich shop.',
          year: 2022,
          genres: const ['Comedy', 'Drama'],
          providerId: _activeProviderId,
          rating: 8.6,
        ),
        CatalogItem(
          id: 'fallout',
          title: 'Fallout',
          kind: MediaKind.series,
          posterUrl: '$_img/w500/swxykNlLBHj79GqAFcCQyox4Ts.jpg',
          bannerUrl: '$_img/w1280/tElnmtQ6yz1PjN1kePNl8yVw0eb.jpg',
          overview:
              'In a future, post-apocalyptic Los Angeles brought about by nuclear decimation, citizens must live in underground bunkers.',
          year: 2024,
          genres: const ['Sci-Fi', 'Action'],
          providerId: _activeProviderId,
          rating: 8.4,
        ),
        CatalogItem(
          id: 'shogun',
          title: 'Shōgun',
          kind: MediaKind.series,
          posterUrl: '$_img/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg',
          bannerUrl: '$_img/w1280/6VmFqAskNVWH94IVzSLvDmWTBjD.jpg',
          overview:
              'In Japan in the year 1600, a mysterious European ship is found ashore and the survivor, John Blackthorne, is forced to serve Lord Toranaga.',
          year: 2024,
          genres: const ['Drama', 'History'],
          providerId: _activeProviderId,
          rating: 8.7,
        ),
        CatalogItem(
          id: 'challengers',
          title: 'Challengers',
          kind: MediaKind.movie,
          posterUrl: '$_img/w500/H6vke7zGiuLkowcWkYkEfztYko.jpg',
          bannerUrl: '$_img/w1280/11G1QiioUuHnR7mTJ2R2oy0E2HP.jpg',
          overview:
              'Tennis player turned coach Tashi has taken her husband Art\'s career in hand. But when he faces off against his former best friend, old passions are reignited.',
          year: 2024,
          genres: const ['Romance', 'Drama'],
          providerId: _activeProviderId,
          rating: 7.2,
        ),
      ];

  HomeFeed home() {
    final items = _catalog;
    return HomeFeed(
      featured: items.first,
      rows: [
        HomeRow(title: 'Trending Now', items: items),
        HomeRow(title: 'New Releases', items: items.reversed.toList()),
        HomeRow(
          title: 'Series',
          items: items.where((e) => e.kind == MediaKind.series).toList(),
        ),
        HomeRow(
          title: 'Movies',
          items: items.where((e) => e.kind == MediaKind.movie).toList(),
        ),
      ],
    );
  }

  List<CatalogItem> search(String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return const [];
    return _catalog
        .where(
          (e) =>
              e.title.toLowerCase().contains(q) ||
              e.genres.any((g) => g.toLowerCase().contains(q)),
        )
        .toList();
  }

  CatalogItem? byId(String id) {
    try {
      return _catalog.firstWhere((e) => e.id == id);
    } catch (_) {
      return null;
    }
  }
}
