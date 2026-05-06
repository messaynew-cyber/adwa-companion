import 'package:flutter/material.dart';
import '../services/api_service.dart';
import 'overlay_widget.dart';

class OverlayService {
  final ApiService api;
  OverlayService(this.api);

  void show(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _OverlayScreen(api: api),
        fullscreenDialog: true,
      ),
    );
  }
}

class _OverlayScreen extends StatelessWidget {
  final ApiService api;
  const _OverlayScreen({required this.api});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          GestureDetector(
            onTap: () => Navigator.of(context).pop(),
            child: Container(color: Colors.black12),
          ),
          AdwaOverlay(api: api),
        ],
      ),
    );
  }
}
