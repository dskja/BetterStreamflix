import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:bsflix/app/bsflix_app.dart';

void main() {
  testWidgets('Bsflix boots to Pulse home', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: BsflixApp()));
    await tester.pumpAndSettle();
    expect(find.textContaining('BetterStreamflix'), findsWidgets);
    expect(find.text('Watch Now'), findsOneWidget);
  });
}
