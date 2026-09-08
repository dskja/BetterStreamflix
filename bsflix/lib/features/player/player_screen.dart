import 'package:chewie/chewie.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:video_player/video_player.dart';

import '../../core/theme/pulse_theme.dart';
import '../../data/repositories/providers.dart';

class PlayerScreen extends ConsumerStatefulWidget {
  const PlayerScreen({super.key, required this.id});

  final String id;

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> {
  VideoPlayerController? _video;
  ChewieController? _chewie;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _boot());
  }

  Future<void> _boot() async {
    final item = ref.read(catalogItemProvider(widget.id));
    if (item == null) {
      setState(() => _error = 'Title not found');
      return;
    }
    try {
      final video = VideoPlayerController.networkUrl(Uri.parse(item.streamUrl));
      await video.initialize();
      final chewie = ChewieController(
        videoPlayerController: video,
        autoPlay: true,
        looping: false,
        allowFullScreen: true,
        materialProgressColors: ChewieProgressColors(
          playedColor: PulseColors.amber,
          handleColor: PulseColors.amberBright,
          bufferedColor: PulseColors.inkSoft,
          backgroundColor: PulseColors.inkPanel,
        ),
      );
      if (!mounted) return;
      setState(() {
        _video = video;
        _chewie = chewie;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'Playback failed: $e');
    }
  }

  @override
  void dispose() {
    _chewie?.dispose();
    _video?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final item = ref.watch(catalogItemProvider(widget.id));
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        leading: IconButton(
          icon: const Icon(Icons.close, color: PulseColors.mist),
          onPressed: () => context.pop(),
        ),
        title: Text(
          item?.title ?? 'Player',
          style: const TextStyle(color: PulseColors.mist),
        ),
      ),
      body: Center(
        child: _error != null
            ? Text(_error!, style: const TextStyle(color: PulseColors.danger))
            : _chewie == null
                ? const CircularProgressIndicator(color: PulseColors.amber)
                : AspectRatio(
                    aspectRatio: _video!.value.aspectRatio == 0
                        ? 16 / 9
                        : _video!.value.aspectRatio,
                    child: Chewie(controller: _chewie!),
                  ),
      ),
    );
  }
}
