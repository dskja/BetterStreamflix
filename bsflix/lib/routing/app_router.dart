import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/pulse_theme.dart';
import '../core/widgets/pulse_widgets.dart';
import '../features/detail/detail_screen.dart';
import '../features/home/home_screen.dart';
import '../features/player/player_screen.dart';
import '../features/providers/providers_screen.dart';
import '../features/search/search_screen.dart';

final _rootKey = GlobalKey<NavigatorState>();

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    navigatorKey: _rootKey,
    initialLocation: '/',
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return AppShell(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/',
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/search',
                builder: (context, state) => const SearchScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/providers',
                builder: (context, state) => const ProvidersScreen(),
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        path: '/title/:id',
        builder: (context, state) =>
            DetailScreen(id: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/play/:id',
        builder: (context, state) =>
            PlayerScreen(id: state.pathParameters['id']!),
      ),
    ],
  );
});

class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: PulseColors.ink,
      extendBody: true,
      body: navigationShell,
      bottomNavigationBar: PulseFloatingNav(
        index: navigationShell.currentIndex,
        onSelect: navigationShell.goBranch,
      ),
    );
  }
}
