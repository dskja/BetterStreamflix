import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme/pulse_theme.dart';
import '../routing/app_router.dart';

class BsflixApp extends ConsumerWidget {
  const BsflixApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    return MaterialApp.router(
      title: 'BetterStreamflix',
      debugShowCheckedModeBanner: false,
      theme: PulseTheme.dark(),
      routerConfig: router,
    );
  }
}
