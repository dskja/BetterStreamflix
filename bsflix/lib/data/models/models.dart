enum MediaKind { movie, series }

class CatalogItem {
  const CatalogItem({
    required this.id,
    required this.title,
    required this.kind,
    required this.posterUrl,
    required this.bannerUrl,
    required this.overview,
    required this.year,
    required this.genres,
    required this.providerId,
    this.rating,
    this.streamUrl =
        'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
  });

  final String id;
  final String title;
  final MediaKind kind;
  final String posterUrl;
  final String bannerUrl;
  final String overview;
  final int year;
  final List<String> genres;
  final String providerId;
  final double? rating;
  final String streamUrl;
}

class StreamProviderInfo {
  const StreamProviderInfo({
    required this.id,
    required this.name,
    required this.language,
    required this.healthy,
    this.favorite = false,
  });

  final String id;
  final String name;
  final String language;
  final bool healthy;
  final bool favorite;

  StreamProviderInfo copyWith({bool? favorite}) => StreamProviderInfo(
        id: id,
        name: name,
        language: language,
        healthy: healthy,
        favorite: favorite ?? this.favorite,
      );
}

class HomeFeed {
  const HomeFeed({
    required this.featured,
    required this.rows,
  });

  final CatalogItem featured;
  final List<HomeRow> rows;
}

class HomeRow {
  const HomeRow({required this.title, required this.items});

  final String title;
  final List<CatalogItem> items;
}
