import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import 'app/bsflix_app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // Prefer bundled/cached fonts; never block startup on network font fetch.
  GoogleFonts.config.allowRuntimeFetching = true;
  runApp(const ProviderScope(child: BsflixApp()));
}
